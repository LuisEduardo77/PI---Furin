package br.com.furin.access.aluno;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.carteirinha.EmissaoDeCarteirinha;

/**
 * Cadastro do aluno em uma única transação (T-19, T-20, T-22; RF04, passo 5; RF05; RF07;
 * RN16): aluno, vínculo de turma vigente, contato principal, consentimento e carteirinha.
 * Se qualquer bloco falhar, a transação desfaz todos, e a foto e o termo digitalizado saem
 * da área privada (CA-RF04-02).
 */
@Service
public class CadastroDeAlunos {

	/** Os dados já conferidos, com o telefone normalizado. */
	public record NovoAluno(String nome, String matricula, long idTurma, String contatoNome, String contatoTelefone,
			String contatoEmail) {
	}

	/**
	 * O termo conferido (RF05). O signatário é o contato principal. {@code protocolo} é nulo
	 * quando a referência é o arquivo digitalizado.
	 */
	public record TermoConferido(String versao, LocalDate assinadoEm, String protocolo) {
	}

	/** Os IDs gravados na transação. */
	public record BlocosGravados(long idAluno, long idContato, long idConsentimento, long idCarteirinha) {
	}

	/** Outro cadastro ficou com a matrícula entre a conferência e a gravação (UNIQUE). */
	public static class MatriculaJaCadastrada extends RuntimeException {

		private static final long serialVersionUID = 1L;

		MatriculaJaCadastrada() {
			super("Matrícula já cadastrada", null, false, false);
		}
	}

	private final RegistrosDeAlunos registros;

	private final AreaPrivada areaPrivada;

	private final EmissaoDeCarteirinha emissao;

	private final TransactionTemplate transacao;

	private final Auditoria auditoria;

	private final Clock relogio;

	public CadastroDeAlunos(RegistrosDeAlunos registros, AreaPrivada areaPrivada, EmissaoDeCarteirinha emissao,
			TransactionTemplate transacao, Auditoria auditoria, Clock relogio) {
		this.registros = registros;
		this.areaPrivada = areaPrivada;
		this.emissao = emissao;
		this.transacao = transacao;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/**
	 * @param termoDigitalizado nulo quando a referência é o protocolo do termo físico
	 * @param login             o funcionário da sessão, gravado como conferente (CA-RF05-04)
	 * @return os IDs gravados; a carteirinha vai para a impressão (RF04, passo 6)
	 * @throws MatriculaJaCadastrada se a matrícula já existir: nada foi salvo
	 */
	public BlocosGravados cadastrar(NovoAluno novo, TermoConferido termo, InputStream foto,
			InputStream termoDigitalizado, String login) throws IOException {
		List<String> arquivos = new ArrayList<>();
		try {
			String fotoChave = guardar(TipoDeDocumento.FOTO, foto, arquivos);
			String referencia = termoDigitalizado == null ? termo.protocolo()
					: guardar(TipoDeDocumento.TERMO, termoDigitalizado, arquivos);
			BlocosGravados blocos = transacao.execute(status -> gravarBlocos(novo, termo, fotoChave, referencia, login));
			// RF04, passo 7: o funcionário (pela sessão) e o ID do aluno, sem dados pessoais
			auditoria.registrar(EventoAuditoria.ALUNO_CADASTRADO, detalhes().id("aluno", blocos.idAluno()));
			return blocos;
		} catch (IOException | RuntimeException falha) {
			// A transação desfaz o banco; os arquivos não fazem parte dela (CA-RF04-02)
			arquivos.forEach(areaPrivada::apagar);
			throw falha;
		}
	}

	/** Os blocos de RF04, RF05 e RF07, dentro da transação de quem chama. */
	BlocosGravados gravarBlocos(NovoAluno novo, TermoConferido termo, String fotoChave, String referencia,
			String login) {
		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		long idAluno;
		try {
			idAluno = registros.inserirAluno(novo.matricula(), novo.nome(), fotoChave, agora);
		} catch (DuplicateKeyException repetida) {
			throw new MatriculaJaCadastrada();
		}
		registros.inserirVinculo(idAluno, novo.idTurma(), agora);
		long idContato = registros.inserirContatoPrincipal(idAluno, novo.contatoNome(), novo.contatoTelefone(),
				novo.contatoEmail());
		// CA-RF05-04: conferente e instante sempre do servidor
		long idConsentimento = registros.inserirConsentimento(idContato, termo.versao(), termo.assinadoEm(),
				referencia, registros.idDoUsuario(login), agora);
		// RF07, emissão no cadastro: código novo, com nova tentativa na colisão (RF06)
		long idCarteirinha = emissao.emitir(idAluno, agora);
		return new BlocosGravados(idAluno, idContato, idConsentimento, idCarteirinha);
	}

	private String guardar(TipoDeDocumento documento, InputStream conteudo, List<String> arquivos)
			throws IOException {
		String chave = areaPrivada.guardar(documento, conteudo);
		arquivos.add(chave);
		return chave;
	}

}
