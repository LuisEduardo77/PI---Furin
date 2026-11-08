package br.com.furin.access.aluno;

import static br.com.furin.access.aluno.ConferenciasDoAluno.CAMPO_MATRICULA;
import static br.com.furin.access.aluno.ConferenciasDoAluno.MATRICULA_REPETIDA;
import static br.com.furin.access.aluno.ConferenciasDoAluno.enviado;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.aluno.CadastroDeAlunos.BlocosGravados;
import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.aluno.CadastroDeAlunos.TermoConferido;
import br.com.furin.access.aluno.RegistrosDeAlunos.TurmaAtiva;
import br.com.furin.access.carteirinha.EmissaoDeCarteirinha.CarteirinhaNaoEmitida;
import br.com.furin.access.config.FurinProperties;
import jakarta.validation.Valid;

/**
 * Telas de alunos (T-19, T-20, T-22; RF04, RF05):
 * <ul>
 * <li>{@code GET /alunos}: a entrada do menu, com o link "Novo aluno". A busca é da T-24;</li>
 * <li>{@code GET /alunos/novo}: o formulário;</li>
 * <li>{@code POST /alunos}: confere tudo e grava o cadastro completo.</li>
 * </ul>
 * Salvo o cadastro (com a carteirinha, RN16), a tela vai para a impressão da carteirinha.
 * Em qualquer erro, nada é salvo, e os dados válidos voltam preenchidos, menos os arquivos,
 * que o navegador não permite manter (RF04; A11 §5.9.9).
 */
@Controller
public class AlunoController {

	private static final Logger LOG = LoggerFactory.getLogger(AlunoController.class);

	private static final String TELA_NOVO = "alunos/novo";
	private static final String CAMPO_ASSINATURA = "termoAssinadoEm";
	private static final String CAMPO_PROTOCOLO = "termoProtocolo";

	/** O único signatário possível no cadastro novo: o contato principal [PROP]. */
	static final String SIGNATARIO_PRINCIPAL = "principal";

	private final CadastroDeAlunos cadastro;

	private final RegistrosDeAlunos registros;

	private final ConferenciasDoAluno conferencias;

	private final String versaoDoTermo;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	public AlunoController(CadastroDeAlunos cadastro, RegistrosDeAlunos registros, ConferenciasDoAluno conferencias,
			FurinProperties propriedades, Clock relogio) {
		this.cadastro = cadastro;
		this.registros = registros;
		this.conferencias = conferencias;
		this.versaoDoTermo = propriedades.termo().versaoAtual();
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
	}

	@ModelAttribute("regraDaFoto")
	String regraDaFoto() {
		return conferencias.regraDaFoto();
	}

	@ModelAttribute("regraDoTermo")
	String regraDoTermo() {
		return conferencias.regraDoTermo();
	}

	/** Espaços nas pontas não contam: um campo só de espaços vale como vazio. */
	@InitBinder("aluno")
	void limparEspacos(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
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
			Authentication funcionario, RedirectAttributes redirecionamento) {
		List<TurmaAtiva> turmas = registros.turmasAtivas();
		model.addAttribute("turmas", turmas);
		Optional<TurmaAtiva> turma = conferencias.turma(form.getIdTurma(), turmas, erros);
		Optional<String> telefone = conferencias.telefone(form.getContatoTelefone(), erros);
		conferencias.dominioDoEmail(form.getContatoEmail(), erros);
		// DEC-45: o link "Abrir cadastro existente" leva à edição do aluno que já usa a matrícula
		conferencias.matricula(form.getMatricula(), null, erros)
				.ifPresent(existente -> model.addAttribute("matriculaEmUso", existente));
		conferencias.foto(form.getFoto(), true, erros);
		Optional<TermoConferido> termo = conferirTermo(form, erros);
		if (erros.hasErrors()) {
			return TELA_NOVO;
		}
		NovoAluno novo = new NovoAluno(form.getNome(), form.getMatricula(), turma.orElseThrow().id(),
				form.getContatoNome(), telefone.orElseThrow(), form.getContatoEmail());
		MultipartFile digitalizado = form.getTermoArquivo();
		try (InputStream foto = form.getFoto().getInputStream();
				InputStream termoDigitalizado = enviado(digitalizado) ? digitalizado.getInputStream() : null) {
			BlocosGravados blocos = cadastro.cadastrar(novo, termo.orElseThrow(), foto, termoDigitalizado,
					funcionario.getName());
			// RF04, passo 6: "Aluno Ana Teste cadastrado. Imprima a carteirinha.", na impressão
			redirecionamento.addFlashAttribute("alunoCadastrado", true);
			return "redirect:/carteirinhas/" + blocos.idCarteirinha() + "/imprimir";
		} catch (MatriculaJaCadastrada repetida) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
			registros.alunoComMatricula(form.getMatricula(), null)
					.ifPresent(existente -> model.addAttribute("matriculaEmUso", existente));
		} catch (CarteirinhaNaoEmitida naoEmitida) {
			// RF06, exceções: todas as tentativas de código colidiram. Nada foi salvo
			erros.reject("carteirinha", "Não foi possível emitir a carteirinha. Tente novamente.");
		} catch (IOException | DataAccessException falha) {
			LOG.error("Falha ao gravar o cadastro de um aluno", falha);
			erros.reject("falha", "Não foi possível salvar o cadastro. Tente novamente.");
		}
		return TELA_NOVO;
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
			conferencias.termoDigitalizado(form.getTermoArquivo(), erros);
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

}
