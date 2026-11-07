package br.com.furin.access.aluno;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import br.com.furin.access.aluno.RegistrosDeAlunos.Consentimento;
import br.com.furin.access.aluno.RevogacaoDoTermo.RevogacaoRecusada;

/**
 * Registro da revogação do termo (T-20; RF05, fluxo A2; DEC-34). A edição do aluno, onde
 * fica o botão "Registrar revogação do termo", é da T-23; até lá, a tela da revogação tem
 * endereço próprio [PROP]: {@code /alunos/{id}/consentimentos/{idConsentimento}/revogacao}.
 */
@Controller
public class RevogacaoController {

	private static final String TELA = "alunos/revogacao";
	private static final String CAMPO_DATA = "data";
	private static final DateTimeFormatter DATA_NA_TELA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/** A data em que o responsável revogou o termo. */
	public static class RevogacaoForm {

		private String data;

		public String getData() {
			return data;
		}

		public void setData(String data) {
			this.data = data;
		}

	}

	private final RegistrosDeAlunos registros;

	private final RevogacaoDoTermo revogacao;

	public RevogacaoController(RegistrosDeAlunos registros, RevogacaoDoTermo revogacao) {
		this.registros = registros;
		this.revogacao = revogacao;
	}

	@GetMapping("/alunos/{idAluno}/consentimentos/{idConsentimento}/revogacao")
	public String exibir(@PathVariable long idAluno, @PathVariable long idConsentimento, Model model) {
		model.addAttribute("consentimento", consentimento(idAluno, idConsentimento));
		model.addAttribute("revogacao", new RevogacaoForm());
		return TELA;
	}

	@PostMapping("/alunos/{idAluno}/consentimentos/{idConsentimento}/revogacao")
	public String registrar(@PathVariable long idAluno, @PathVariable long idConsentimento,
			@ModelAttribute("revogacao") RevogacaoForm form, BindingResult erros, Model model) {
		Consentimento consentimento = consentimento(idAluno, idConsentimento);
		model.addAttribute("consentimento", consentimento);
		Optional<LocalDate> data = conferirData(form.getData(), consentimento, erros);
		if (erros.hasErrors() || data.isEmpty()) {
			return TELA;
		}
		try {
			revogacao.registrar(idAluno, idConsentimento, data.get());
			model.addAttribute("revogacaoRegistrada", true);
		} catch (RevogacaoRecusada recusa) {
			erros.reject(recusa.getMotivo().name(), mensagem(recusa));
		}
		return TELA;
	}

	private Consentimento consentimento(long idAluno, long idConsentimento) {
		return registros.consentimentoDoAluno(idAluno, idConsentimento)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/** Textos [PROP]. A data não pode ser futura nem anterior ao dia da conferência. */
	private Optional<LocalDate> conferirData(String texto, Consentimento consentimento, BindingResult erros) {
		LocalDate data;
		try {
			data = LocalDate.parse(texto == null ? "" : texto.strip());
		} catch (DateTimeParseException semData) {
			erros.rejectValue(CAMPO_DATA, "vazia", "Informe a data em que o responsável revogou o termo.");
			return Optional.empty();
		}
		LocalDate conferencia = revogacao.diaDaConferencia(consentimento);
		if (data.isAfter(revogacao.hoje())) {
			erros.rejectValue(CAMPO_DATA, "futura", "A data da revogação não pode ser posterior a hoje.");
		} else if (data.isBefore(conferencia)) {
			erros.rejectValue(CAMPO_DATA, "anterior", "A data da revogação não pode ser anterior à conferência do "
					+ "termo, em " + DATA_NA_TELA.format(conferencia) + ".");
		}
		return Optional.of(data);
	}

	private static String mensagem(RevogacaoRecusada recusa) {
		return switch (recusa.getMotivo()) {
			case JA_REVOGADO -> "Este termo já tinha sido revogado. Nada foi alterado.";
			case SAIDA_PENDENTE -> "O aluno está na escola ou tem uma liberação pendente. Registre a saída antes "
					+ "de registrar a revogação.";
			case NAO_ENCONTRADO -> "Termo não encontrado para este aluno.";
		};
	}

}
