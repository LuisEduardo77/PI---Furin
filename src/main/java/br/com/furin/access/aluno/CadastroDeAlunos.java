package br.com.furin.access.aluno;

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

/**
 * Cadastro do aluno em uma única transação (T-19, T-20; RF04, passo 5; RF05; RN16).
 * <p>
 * O RF04 só salva o cadastro com <b>todos</b> os blocos completos (DEC-44). Aluno, vínculo,
 * contato principal e consentimento já são gravados; a carteirinha é da T-22. Por isso,
 * por enquanto, o bloco que falta faz o cadastro falhar como qualquer outro bloco:
 * {@link CadastroIncompleto} desfaz a transação, e a foto e o termo digitalizado saem da
 * área privada (CA-RF04-02). Nenhum aluno é salvo até a T-22.
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
	record BlocosGravados(long idAluno, long idContato, long idConsentimento) {
	}

	/** Falta a carteirinha (T-22). A transação foi desfeita. */
	public static class CadastroIncompleto extends RuntimeException {

		private static final long serialVersionUID = 1L;

		CadastroIncompleto() {
			super("Falta a carteirinha", null, false, false);
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
	 * @param termoDigitalizado nulo quando a referência é o protocolo do termo físico
	 * @param login             o funcionário da sessão, gravado como conferente (CA-RF05-04)
	 * @throws CadastroIncompleto    sempre que tudo der certo, até a T-22: nada foi salvo
	 * @throws MatriculaJaCadastrada se a matrícula já existir: nada foi salvo
	 */
	public void cadastrar(NovoAluno novo, TermoConferido termo, InputStream foto, InputStream termoDigitalizado,
			String login) throws IOException {
		List<String> arquivos = new ArrayList<>();
		try {
			String fotoChave = guardar(TipoDeDocumento.FOTO, foto, arquivos);
			String referencia = termoDigitalizado == null ? termo.protocolo()
					: guardar(TipoDeDocumento.TERMO, termoDigitalizado, arquivos);
			transacao.executeWithoutResult(status -> {
				gravarBlocos(novo, termo, fotoChave, referencia, login);
				// RF04, passo 5.6: a carteirinha é da T-22 (DEC-44)
				throw new CadastroIncompleto();
			});
		} catch (IOException | RuntimeException falha) {
			// A transação desfaz o banco; os arquivos não fazem parte dela (CA-RF04-02)
			arquivos.forEach(areaPrivada::apagar);
			throw falha;
		}
	}

	/** Os blocos de RF04 e RF05, dentro da transação de quem chama. */
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
		return new BlocosGravados(idAluno, idContato, idConsentimento);
	}

	private String guardar(TipoDeDocumento documento, InputStream conteudo, List<String> arquivos)
			throws IOException {
		String chave = areaPrivada.guardar(documento, conteudo);
		arquivos.add(chave);
		return chave;
	}

}
