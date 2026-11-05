package br.com.furin.access.aluno;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;

/**
 * Cadastro do aluno em uma única transação (T-19; RF04, passo 5; RN16).
 * <p>
 * O RF04 só salva o cadastro com <b>todos</b> os blocos completos, e o termo (T-20) e a
 * carteirinha (T-22) ainda não existem (decisão do integrante em 05/11/2026). Por isso a
 * transação roda até onde dá, e os blocos que faltam fazem o cadastro falhar como
 * qualquer outro bloco: {@link CadastroIncompleto} desfaz os registros, e a foto sai da
 * área privada (CA-RF04-02). Nenhum aluno é salvo até a T-22.
 */
@Service
public class CadastroDeAlunos {

	/** Os dados já conferidos, com o telefone normalizado. */
	public record NovoAluno(String nome, String matricula, long idTurma, String contatoNome, String contatoTelefone,
			String contatoEmail) {
	}

	/** Faltam o termo e a carteirinha. A transação foi desfeita. */
	public static class CadastroIncompleto extends RuntimeException {

		private static final long serialVersionUID = 1L;

		CadastroIncompleto() {
			super("Faltam o termo e a carteirinha", null, false, false);
		}
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

	private final TransactionTemplate transacao;

	private final Clock relogio;

	public CadastroDeAlunos(RegistrosDeAlunos registros, AreaPrivada areaPrivada, TransactionTemplate transacao,
			Clock relogio) {
		this.registros = registros;
		this.areaPrivada = areaPrivada;
		this.transacao = transacao;
		this.relogio = relogio;
	}

	/**
	 * @throws CadastroIncompleto    sempre que tudo der certo, até a T-22: nada foi salvo
	 * @throws MatriculaJaCadastrada se a matrícula já existir: nada foi salvo
	 */
	public void cadastrar(NovoAluno novo, InputStream foto) throws IOException {
		String fotoChave = areaPrivada.guardar(TipoDeDocumento.FOTO, foto);
		try {
			transacao.executeWithoutResult(status -> gravar(novo, fotoChave));
		} catch (RuntimeException falha) {
			// A transação desfaz o banco; o arquivo não faz parte dela (CA-RF04-02)
			areaPrivada.apagar(fotoChave);
			throw falha;
		}
	}

	private void gravar(NovoAluno novo, String fotoChave) {
		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		long idAluno;
		try {
			idAluno = registros.inserirAluno(novo.matricula(), novo.nome(), fotoChave, agora);
		} catch (DuplicateKeyException repetida) {
			throw new MatriculaJaCadastrada();
		}
		registros.inserirVinculo(idAluno, novo.idTurma(), agora);
		registros.inserirContatoPrincipal(idAluno, novo.contatoNome(), novo.contatoTelefone(), novo.contatoEmail());
		// RF04, passo 5: o consentimento entra na T-20 e a carteirinha, na T-22
		throw new CadastroIncompleto();
	}

}
