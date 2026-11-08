package br.com.furin.access.aluno;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.RegistrosDeAlunos.CadastroAtual;
import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;

/**
 * Edição do aluno (T-23; RF08). Cada mudança segue a sua regra:
 * <ul>
 * <li>nome, matrícula e foto atualizam o aluno; a foto antiga sai da área privada depois da
 * gravação (DEC-28, D10);</li>
 * <li>a turma encerra o vínculo vigente e cria um novo, e o histórico continua com a turma
 * antiga (CA-RF08-02);</li>
 * <li>telefone e e-mail atualizam o contato principal; outra pessoa vira um contato novo, e
 * o anterior deixa de ser o principal (fluxo A1);</li>
 * <li>o aviso por e-mail muda a pedido do responsável, com {@code PREFERENCIA_AVISO_ALTERADA}
 * (fluxo A5; DEC-38).</li>
 * </ul>
 * Tudo numa transação, com o aluno bloqueado (RN08). A última gravação prevalece (DEC-28, D10).
 */
@Service
public class EdicaoDoAluno {

	/** Os dados revisados, já conferidos, com o telefone normalizado. Vai para a sessão até a confirmação. */
	public record Alteracao(String nome, String matricula, long idTurma, String turma, boolean substituirContato,
			String contatoNome, String contatoTelefone, String contatoEmail, boolean recebeAviso) implements Serializable {
	}

	/** O nome do detalhe com o ID do aluno, nos três eventos. */
	private static final String ALUNO = "aluno";

	/** Uma linha do resumo "antes → depois" (A11 §5.9.12). */
	public record Diferenca(String campo, String antes, String depois) {
	}

	/** RF08, fluxo A2: o aluno está na escola ou tem uma liberação pendente. */
	public static class SaidaPendente extends RuntimeException {

		private static final long serialVersionUID = 1L;

		SaidaPendente() {
			super("Saída pendente", null, false, false);
		}
	}

	private static final String SIM = "Sim";
	private static final String NAO = "Não";

	private final RegistrosDeAlunos registros;

	private final ConferenciasDoAluno conferencias;

	private final AreaPrivada areaPrivada;

	private final TransactionTemplate transacao;

	private final Auditoria auditoria;

	private final Clock relogio;

	public EdicaoDoAluno(RegistrosDeAlunos registros, ConferenciasDoAluno conferencias, AreaPrivada areaPrivada,
			TransactionTemplate transacao, Auditoria auditoria, Clock relogio) {
		this.registros = registros;
		this.conferencias = conferencias;
		this.areaPrivada = areaPrivada;
		this.transacao = transacao;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/**
	 * O resumo "antes → depois" (RF08, passo 3). Os nomes dos campos são os do log
	 * {@code ALUNO_EDITADO} quando a linha é de um campo do aluno ou do contato [PROP].
	 */
	public List<Diferenca> diferencas(CadastroAtual atual, Alteracao nova, boolean fotoNova) {
		List<Diferenca> linhas = new ArrayList<>();
		comparar(linhas, "Nome", atual.nome(), nova.nome());
		comparar(linhas, "Matrícula", atual.matricula(), nova.matricula());
		comparar(linhas, "Turma", atual.turma(), nova.turma());
		if (fotoNova) {
			linhas.add(new Diferenca("Foto", "Foto atual", "Nova foto enviada"));
		}
		if (nova.substituirContato()) {
			linhas.add(new Diferenca("Contato principal", atual.contatoNome(),
					nova.contatoNome() + " (novo contato; " + atual.contatoNome() + " deixa de receber os avisos)"));
			comparar(linhas, "Telefone do contato", atual.contatoTelefone(), nova.contatoTelefone());
			comparar(linhas, "E-mail do contato", atual.contatoEmail(), nova.contatoEmail());
		} else {
			if (!mesmoTelefone(atual.contatoTelefone(), nova.contatoTelefone())) {
				linhas.add(new Diferenca("Telefone do contato", atual.contatoTelefone(), nova.contatoTelefone()));
			}
			comparar(linhas, "E-mail do contato", atual.contatoEmail(), nova.contatoEmail());
		}
		if (atual.recebeAviso() != nova.recebeAviso()) {
			linhas.add(new Diferenca("Aviso por e-mail", atual.recebeAviso() ? SIM : NAO,
					nova.recebeAviso() ? SIM : NAO));
		}
		return linhas;
	}

	/**
	 * RF08, passo 4: grava a alteração confirmada.
	 *
	 * @param fotoNova nula quando a foto atual continua
	 * @throws MatriculaJaCadastrada se outro aluno ficou com a matrícula; nada muda
	 */
	public void gravar(long idAluno, Alteracao nova, ByteArrayResource fotoNova) throws IOException {
		String chaveNova = null;
		if (fotoNova != null) {
			try (InputStream conteudo = fotoNova.getInputStream()) {
				chaveNova = areaPrivada.guardar(TipoDeDocumento.FOTO, conteudo);
			}
		}
		String fotoGravada = chaveNova;
		String fotoAntiga;
		try {
			fotoAntiga = transacao.execute(status -> aplicar(idAluno, nova, fotoGravada));
		} catch (RuntimeException falha) {
			if (chaveNova != null) {
				areaPrivada.apagar(chaveNova);
			}
			throw falha;
		}
		// DEC-28, D10: a foto antiga sai depois da gravação, por minimização
		if (chaveNova != null && fotoAntiga != null) {
			areaPrivada.apagar(fotoAntiga);
		}
	}

	/**
	 * RF08, fluxo A2: inativa o aluno, se não houver saída pendente (DEC-04, RN06). Novas
	 * leituras passam a ser recusadas (RN09), e o histórico continua.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void inativar(long idAluno) {
		registros.bloquearAluno(idAluno);
		if (registros.comSaidaPendente(idAluno)) {
			throw new SaidaPendente();
		}
		registros.inativarAluno(idAluno);
		auditoria.registrar(EventoAuditoria.ALUNO_EDITADO, detalhes().id(ALUNO, idAluno).campos(List.of("ativo")));
	}

	/** @return a chave da foto antiga, se ela foi trocada */
	private String aplicar(long idAluno, Alteracao nova, String chaveNova) {
		registros.bloquearAluno(idAluno);
		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		CadastroAtual atual = registros.cadastroAtual(idAluno, agora).orElseThrow();
		List<String> campos = new ArrayList<>();

		String foto = chaveNova == null ? atual.fotoChave() : chaveNova;
		marcar(campos, "nome", !Objects.equals(atual.nome(), nova.nome()));
		marcar(campos, "matricula", !Objects.equals(atual.matricula(), nova.matricula()));
		marcar(campos, "foto", chaveNova != null);
		try {
			registros.atualizarAluno(idAluno, nova.nome(), nova.matricula(), foto);
		} catch (DuplicateKeyException repetida) {
			throw new MatriculaJaCadastrada();
		}

		if (!Objects.equals(atual.idTurma(), nova.idTurma())) {
			// CA-RF08-02: o vínculo antigo fica com o histórico; o novo começa agora
			if (atual.idVinculo() != null) {
				registros.encerrarVinculo(atual.idVinculo(), agora);
			}
			registros.inserirVinculo(idAluno, nova.idTurma(), agora);
			campos.add("turma");
		}

		gravarContato(idAluno, atual, nova, campos);

		if (!campos.isEmpty()) {
			// CA-RF08-07: só os nomes dos campos, sem os valores
			auditoria.registrar(EventoAuditoria.ALUNO_EDITADO, detalhes().id(ALUNO, idAluno).campos(campos));
		}
		if (atual.recebeAviso() != nova.recebeAviso()) {
			// DEC-38: o autor é o funcionário da sessão
			auditoria.registrar(EventoAuditoria.PREFERENCIA_AVISO_ALTERADA, detalhes().id(ALUNO, idAluno)
					.texto("aviso", nova.recebeAviso() ? "LIGADO" : "DESLIGADO"));
		}
		return chaveNova == null ? null : atual.fotoChave();
	}

	private void gravarContato(long idAluno, CadastroAtual atual, Alteracao nova, List<String> campos) {
		if (nova.substituirContato() || atual.idContato() == null) {
			// Fluxo A1: um contato novo; o banco garante um só principal ativo
			if (atual.idContato() != null) {
				registros.deixarDeSerPrincipal(atual.idContato());
			}
			registros.inserirContatoPrincipal(idAluno, nova.contatoNome(), nova.contatoTelefone(), nova.contatoEmail(),
					nova.recebeAviso());
			campos.add("contatoPrincipal");
			return;
		}
		marcar(campos, "contatoTelefone", !mesmoTelefone(atual.contatoTelefone(), nova.contatoTelefone()));
		marcar(campos, "contatoEmail", !Objects.equals(atual.contatoEmail(), nova.contatoEmail()));
		registros.atualizarContato(atual.idContato(), nova.contatoTelefone(), nova.contatoEmail(), nova.recebeAviso());
	}

	/** Um telefone gravado antes da normalização, como "(11) 90000-0001", é o mesmo que "+55 11 900000001". */
	private boolean mesmoTelefone(String atual, String novo) {
		String comparavel = atual == null ? null : conferencias.telefoneNormalizado(atual).orElse(atual);
		return Objects.equals(comparavel, novo);
	}

	private static void comparar(List<Diferenca> linhas, String campo, String antes, String depois) {
		if (!Objects.equals(antes, depois)) {
			linhas.add(new Diferenca(campo, antes == null ? "—" : antes, depois));
		}
	}

	private static void marcar(List<String> campos, String campo, boolean mudou) {
		if (mudou) {
			campos.add(campo);
		}
	}

}
