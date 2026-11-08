package br.com.furin.access.aluno;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import br.com.furin.access.aluno.RegistrosDeAlunos.TurmaAtiva;
import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.arquivo.ArquivoRecusado;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.FurinProperties.RegraArquivo;

/**
 * As conferências que o cadastro (RF04) e a edição (RF08) fazem igual, com as mensagens do
 * RF04 e do RF06. Os dois formulários usam os mesmos nomes de campo: {@code matricula},
 * {@code idTurma}, {@code foto}, {@code contatoTelefone} e {@code contatoEmail}.
 */
@Component
public class ConferenciasDoAluno {

	/** RF06; A11 §5.9.10. O link "Abrir cadastro existente" vem do ID devolvido (DEC-45). */
	static final String MATRICULA_REPETIDA = "Esta matrícula já está cadastrada. Confira a matrícula ou "
			+ "localize o cadastro existente para editá-lo.";

	static final String CAMPO_MATRICULA = "matricula";

	private static final String CAMPO_TELEFONE = "contatoTelefone";
	private static final String CAMPO_EMAIL = "contatoEmail";
	private static final String CAMPO_FOTO = "foto";

	private final RegistrosDeAlunos registros;

	private final AreaPrivada areaPrivada;

	private final String ddi;

	private final String regraDaFoto;

	private final String regraDoTermo;

	public ConferenciasDoAluno(RegistrosDeAlunos registros, AreaPrivada areaPrivada, FurinProperties propriedades) {
		this.registros = registros;
		this.areaPrivada = areaPrivada;
		this.ddi = propriedades.contato().ddi();
		this.regraDaFoto = regraEmTexto(propriedades.arquivos().foto());
		this.regraDoTermo = regraEmTexto(propriedades.arquivos().termo());
	}

	/** "JPEG ou PNG, até 2 MB", igual a {@code furin.arquivos.foto.*}. */
	public String regraDaFoto() {
		return regraDaFoto;
	}

	/** "PDF, JPEG ou PNG, até 5 MB", igual a {@code furin.arquivos.termo.*}. */
	public String regraDoTermo() {
		return regraDoTermo;
	}

	Optional<TurmaAtiva> turma(String idTurma, List<TurmaAtiva> turmas, BindingResult erros) {
		Optional<TurmaAtiva> turma = turmas.stream().filter(ativa -> String.valueOf(ativa.id()).equals(idTurma))
				.findFirst();
		if (turma.isEmpty()) {
			erros.rejectValue("idTurma", "inativa", "Selecione uma turma ativa.");
		}
		return turma;
	}

	/** RF04: gravado com DDI e DDD. */
	Optional<String> telefone(String digitado, BindingResult erros) {
		Optional<String> telefone = TelefoneDoContato.normalizar(digitado, ddi);
		if (telefone.isEmpty() && !erros.hasFieldErrors(CAMPO_TELEFONE)) {
			erros.rejectValue(CAMPO_TELEFONE, "invalido", "Informe o telefone com DDD. Exemplo: (11) 90000-0000.");
		}
		return telefone;
	}

	/** O mesmo telefone, como ficaria gravado; vazio se não for um telefone válido. */
	Optional<String> telefoneNormalizado(String telefone) {
		return TelefoneDoContato.normalizar(telefone, ddi);
	}

	/** {@code @Email} aceita "carla@escola"; o aviso ao responsável precisa de um domínio completo. */
	void dominioDoEmail(String email, BindingResult erros) {
		if (!erros.hasFieldErrors(CAMPO_EMAIL) && !email.substring(email.lastIndexOf('@')).contains(".")) {
			erros.rejectValue(CAMPO_EMAIL, "dominio", DadosDoAlunoForm.EMAIL_INVALIDO);
		}
	}

	/**
	 * RF06: procura em todos os alunos, ativos e inativos; na edição, {@code idIgnorado} é o
	 * próprio aluno. Os espaços das pontas já saíram ({@code StringTrimmerEditor}).
	 *
	 * @return o aluno que já usa a matrícula, para o link "Abrir cadastro existente"
	 */
	Optional<Long> matricula(String matricula, Long idIgnorado, BindingResult erros) {
		if (erros.hasFieldErrors(CAMPO_MATRICULA)) {
			return Optional.empty();
		}
		Optional<Long> emUso = registros.alunoComMatricula(matricula, idIgnorado);
		emUso.ifPresent(aluno -> erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA));
		return emUso;
	}

	/** @param obrigatoria no cadastro, sim; na edição, sem foto nova a atual continua (CA-RF08-04) */
	void foto(MultipartFile foto, boolean obrigatoria, BindingResult erros) {
		if (!enviado(foto)) {
			if (obrigatoria) {
				erros.rejectValue(CAMPO_FOTO, "ausente", "Envie a foto do aluno.");
			}
			return;
		}
		try (InputStream conteudo = foto.getInputStream()) {
			areaPrivada.conferir(TipoDeDocumento.FOTO, conteudo);
		} catch (ArquivoRecusado | IOException recusada) {
			erros.rejectValue(CAMPO_FOTO, "regra", "A foto precisa ser " + regraDaFoto + ".");
		}
	}

	void termoDigitalizado(MultipartFile arquivo, BindingResult erros) {
		try (InputStream conteudo = arquivo.getInputStream()) {
			areaPrivada.conferir(TipoDeDocumento.TERMO, conteudo);
		} catch (ArquivoRecusado | IOException recusado) {
			erros.rejectValue("termoArquivo", "regra", "O termo digitalizado precisa ser " + regraDoTermo + ".");
		}
	}

	static boolean enviado(MultipartFile arquivo) {
		return arquivo != null && !arquivo.isEmpty();
	}

	/** "JPEG ou PNG, até 2 MB" ou "PDF, JPEG ou PNG, até 5 MB". */
	private static String regraEmTexto(RegraArquivo regra) {
		List<String> formatos = regra.tipos().stream()
				.map(tipo -> tipo.substring(tipo.indexOf('/') + 1).toUpperCase(Locale.ROOT))
				.toList();
		String lista = formatos.size() == 1 ? formatos.get(0)
				: String.join(", ", formatos.subList(0, formatos.size() - 1)) + " ou " + formatos.getLast();
		return lista + ", até " + regra.tamanhoMax().toMegabytes() + " MB";
	}

}
