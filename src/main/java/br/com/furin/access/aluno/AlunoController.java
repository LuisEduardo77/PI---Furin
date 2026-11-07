package br.com.furin.access.aluno;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import br.com.furin.access.aluno.CadastroDeAlunos.CadastroIncompleto;
import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.aluno.CadastroDeAlunos.TermoConferido;
import br.com.furin.access.aluno.RegistrosDeAlunos.TurmaAtiva;
import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.arquivo.ArquivoRecusado;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.FurinProperties.RegraArquivo;
import jakarta.validation.Valid;

/**
 * Telas de alunos (T-19, T-20; RF04, RF05):
 * <ul>
 * <li>{@code GET /alunos}: a entrada do menu, com o link "Novo aluno". A busca é da T-24;</li>
 * <li>{@code GET /alunos/novo}: o formulário;</li>
 * <li>{@code POST /alunos}: confere tudo e tenta o cadastro completo.</li>
 * </ul>
 * Até a T-22, nada é salvo (ver {@link CadastroDeAlunos}), e a tela diz isso com clareza.
 * Em qualquer erro, os dados válidos voltam preenchidos, menos a foto, que o navegador não
 * permite manter (RF04; A11 §5.9.9).
 */
@Controller
public class AlunoController {

	private static final Logger LOG = LoggerFactory.getLogger(AlunoController.class);

	private static final String TELA_NOVO = "alunos/novo";
	private static final String CAMPO_FOTO = "foto";
	private static final String CAMPO_TURMA = "idTurma";
	private static final String CAMPO_MATRICULA = "matricula";
	private static final String CAMPO_EMAIL = "contatoEmail";
	private static final String CAMPO_ASSINATURA = "termoAssinadoEm";
	private static final String CAMPO_PROTOCOLO = "termoProtocolo";

	/** O único signatário possível no cadastro novo: o contato principal [PROP]. */
	static final String SIGNATARIO_PRINCIPAL = "principal";
	private static final String MATRICULA_REPETIDA = "Esta matrícula já está cadastrada. Confira a matrícula ou "
			+ "localize o cadastro existente para editá-lo.";

	private final CadastroDeAlunos cadastro;

	private final RegistrosDeAlunos registros;

	private final AreaPrivada areaPrivada;

	private final String ddi;

	private final String regraDaFoto;

	private final String regraDoTermo;

	private final String versaoDoTermo;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	public AlunoController(CadastroDeAlunos cadastro, RegistrosDeAlunos registros, AreaPrivada areaPrivada,
			FurinProperties propriedades, Clock relogio) {
		this.cadastro = cadastro;
		this.registros = registros;
		this.areaPrivada = areaPrivada;
		this.ddi = propriedades.contato().ddi();
		this.regraDaFoto = regraEmTexto(propriedades.arquivos().foto());
		this.regraDoTermo = regraEmTexto(propriedades.arquivos().termo());
		this.versaoDoTermo = propriedades.termo().versaoAtual();
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
	}

	@ModelAttribute("regraDoTermo")
	String regraDoTermo() {
		return regraDoTermo;
	}

	/** Espaços nas pontas não contam: um campo só de espaços vale como vazio. */
	@InitBinder("aluno")
	void limparEspacos(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
	}

	@ModelAttribute("regraDaFoto")
	String regraDaFoto() {
		return regraDaFoto;
	}

	@GetMapping("/alunos")
	public String inicio() {
		return "alunos/inicio";
	}

	@GetMapping("/alunos/novo")
	public String novo(Model model) {
		AlunoForm form = new AlunoForm();
		// RF05: o contato principal vem como signatário, e a versão vigente já preenchida (CA-RF05-10)
		form.setTermoSignatario(SIGNATARIO_PRINCIPAL);
		form.setTermoVersao(versaoDoTermo);
		model.addAttribute("aluno", form);
		model.addAttribute("turmas", registros.turmasAtivas());
		return TELA_NOVO;
	}

	@PostMapping("/alunos")
	public String cadastrar(@Valid @ModelAttribute("aluno") AlunoForm form, BindingResult erros, Model model,
			Authentication funcionario) {
		List<TurmaAtiva> turmas = registros.turmasAtivas();
		model.addAttribute("turmas", turmas);
		Optional<TurmaAtiva> turma = conferirTurma(form, turmas, erros);
		Optional<String> telefone = conferirTelefone(form, erros);
		conferirDominioDoEmail(form, erros);
		conferirMatricula(form, erros);
		conferirFoto(form.getFoto(), erros);
		Optional<TermoConferido> termo = conferirTermo(form, erros);
		if (erros.hasErrors()) {
			return TELA_NOVO;
		}
		NovoAluno novo = new NovoAluno(form.getNome(), form.getMatricula(), turma.orElseThrow().id(),
				form.getContatoNome(), telefone.orElseThrow(), form.getContatoEmail());
		MultipartFile digitalizado = form.getTermoArquivo();
		try (InputStream foto = form.getFoto().getInputStream();
				InputStream termoDigitalizado = enviado(digitalizado) ? digitalizado.getInputStream() : null) {
			cadastro.cadastrar(novo, termo.orElseThrow(), foto, termoDigitalizado, funcionario.getName());
		} catch (CadastroIncompleto faltamBlocos) {
			model.addAttribute("cadastroIncompleto", true);
		} catch (MatriculaJaCadastrada repetida) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
		} catch (IOException | DataAccessException falha) {
			LOG.error("Falha ao gravar o cadastro de um aluno", falha);
			erros.reject("falha", "Não foi possível salvar o cadastro. Tente novamente.");
		}
		return TELA_NOVO;
	}

	private static Optional<TurmaAtiva> conferirTurma(AlunoForm form, List<TurmaAtiva> turmas, BindingResult erros) {
		Optional<TurmaAtiva> turma = turmas.stream()
				.filter(ativa -> String.valueOf(ativa.id()).equals(form.getIdTurma()))
				.findFirst();
		if (turma.isEmpty()) {
			erros.rejectValue(CAMPO_TURMA, "inativa", "Selecione uma turma ativa.");
		}
		return turma;
	}

	private Optional<String> conferirTelefone(AlunoForm form, BindingResult erros) {
		Optional<String> telefone = TelefoneDoContato.normalizar(form.getContatoTelefone(), ddi);
		if (telefone.isEmpty() && !erros.hasFieldErrors("contatoTelefone")) {
			erros.rejectValue("contatoTelefone", "invalido", "Informe o telefone com DDD. Exemplo: (11) 90000-0000.");
		}
		return telefone;
	}

	/** {@code @Email} aceita "carla@escola"; o aviso ao responsável precisa de um domínio completo. */
	private static void conferirDominioDoEmail(AlunoForm form, BindingResult erros) {
		String email = form.getContatoEmail();
		if (!erros.hasFieldErrors(CAMPO_EMAIL) && !email.substring(email.lastIndexOf('@')).contains(".")) {
			erros.rejectValue(CAMPO_EMAIL, "dominio", AlunoForm.EMAIL_INVALIDO);
		}
	}

	private void conferirMatricula(AlunoForm form, BindingResult erros) {
		if (!erros.hasFieldErrors(CAMPO_MATRICULA) && registros.matriculaCadastrada(form.getMatricula())) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
		}
	}

	private void conferirFoto(MultipartFile foto, BindingResult erros) {
		if (foto == null || foto.isEmpty()) {
			erros.rejectValue(CAMPO_FOTO, "ausente", "Envie a foto do aluno.");
			return;
		}
		try (InputStream conteudo = foto.getInputStream()) {
			areaPrivada.conferir(TipoDeDocumento.FOTO, conteudo);
		} catch (ArquivoRecusado | IOException recusada) {
			erros.rejectValue(CAMPO_FOTO, "regra", "A foto precisa ser " + regraDaFoto + ".");
		}
	}

	/**
	 * RF05, passo 2: todos os campos, a data que não pode ser futura no fuso da escola (RN10)
	 * e o signatário. A caixa de confirmação é conferida pelo {@code @AssertTrue} do formulário.
	 */
	private Optional<TermoConferido> conferirTermo(AlunoForm form, BindingResult erros) {
		if (!SIGNATARIO_PRINCIPAL.equals(form.getTermoSignatario())) {
			// CA-RF05-05: signatário forçado na requisição. Mensagem genérica (RF05, exceções)
			erros.reject("signatario", "Não foi possível conferir o signatário do termo. Recarregue a página e tente "
					+ "novamente.");
		}
		Optional<LocalDate> assinadoEm = conferirDataDaAssinatura(form.getTermoAssinadoEm(), erros);
		boolean comProtocolo = form.getTermoProtocolo() != null && !form.getTermoProtocolo().isBlank();
		boolean comArquivo = enviado(form.getTermoArquivo());
		if (!comProtocolo && !comArquivo && !erros.hasFieldErrors(CAMPO_PROTOCOLO)) {
			erros.rejectValue(CAMPO_PROTOCOLO, "ausente", "Informe o protocolo do termo físico ou envie a cópia "
					+ "digitalizada.");
		} else if (comProtocolo && comArquivo) {
			erros.rejectValue(CAMPO_PROTOCOLO, "dois", "Informe o protocolo ou envie a cópia digitalizada, não os "
					+ "dois.");
		}
		if (comArquivo) {
			conferirTermoDigitalizado(form.getTermoArquivo(), erros);
		}
		if (assinadoEm.isEmpty() || form.getTermoVersao() == null) {
			return Optional.empty();
		}
		return Optional.of(new TermoConferido(form.getTermoVersao(), assinadoEm.get(),
				comArquivo ? null : form.getTermoProtocolo()));
	}

	private Optional<LocalDate> conferirDataDaAssinatura(String texto, BindingResult erros) {
		LocalDate data;
		try {
			data = LocalDate.parse(texto == null ? "" : texto);
		} catch (DateTimeParseException semData) {
			erros.rejectValue(CAMPO_ASSINATURA, "vazia", "Informe a data da assinatura do termo.");
			return Optional.empty();
		}
		// CA-RF05-03: "hoje" é a data local da conferência, no fuso da escola
		if (data.isAfter(LocalDate.now(relogio.withZone(fusoDaEscola)))) {
			erros.rejectValue(CAMPO_ASSINATURA, "futura", "A data da assinatura não pode ser posterior a hoje.");
			return Optional.empty();
		}
		return Optional.of(data);
	}

	private void conferirTermoDigitalizado(MultipartFile arquivo, BindingResult erros) {
		try (InputStream conteudo = arquivo.getInputStream()) {
			areaPrivada.conferir(TipoDeDocumento.TERMO, conteudo);
		} catch (ArquivoRecusado | IOException recusado) {
			erros.rejectValue("termoArquivo", "regra", "O termo digitalizado precisa ser " + regraDoTermo + ".");
		}
	}

	private static boolean enviado(MultipartFile arquivo) {
		return arquivo != null && !arquivo.isEmpty();
	}

	/** "JPEG ou PNG, até 2 MB" ou "PDF, JPEG ou PNG, até 5 MB", sempre igual a {@code furin.arquivos.*}. */
	private static String regraEmTexto(RegraArquivo regra) {
		List<String> formatos = regra.tipos().stream()
				.map(tipo -> tipo.substring(tipo.indexOf('/') + 1).toUpperCase(Locale.ROOT))
				.toList();
		String lista = formatos.size() == 1 ? formatos.get(0)
				: String.join(", ", formatos.subList(0, formatos.size() - 1)) + " ou " + formatos.getLast();
		return lista + ", até " + regra.tamanhoMax().toMegabytes() + " MB";
	}

}
