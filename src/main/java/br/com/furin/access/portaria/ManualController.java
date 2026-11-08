package br.com.furin.access.portaria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.aluno.BuscaDeAlunos;
import br.com.furin.access.aluno.BuscaDeAlunos.Escopo;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.RegistroManual.Ficha;
import br.com.furin.access.portaria.RegistroManual.Motivo;
import br.com.furin.access.portaria.RegistroManual.RegistroManualRecusado;
import br.com.furin.access.portaria.RegistroManual.Registrado;
import jakarta.validation.Valid;

/**
 * Registro manual na portaria (T-32; RF12; arquitetura §4, {@code GET/POST /portaria/manual}):
 * <ul>
 * <li>{@code GET /portaria/manual}: a busca do aluno, só entre os ativos (RF11; T-24,
 * {@link Escopo#SO_ATIVOS}), com "Registrar manualmente para Ana Teste";</li>
 * <li>{@code GET /portaria/manual/{id}}: o formulário com nome, matrícula, turma e o tipo
 * calculado, a data e a hora já preenchidas com o momento atual e o motivo;</li>
 * <li>{@code POST /portaria/manual/{id}/revisar}: confere e mostra a confirmação com aluno,
 * tipo, horário e motivo, com Corrigir e Confirmar (A11 §5.9.12);</li>
 * <li>{@code POST /portaria/manual/{id}/corrigir}: volta ao formulário com os valores;</li>
 * <li>{@code POST /portaria/manual/{id}}: grava e volta à busca com a confirmação.</li>
 * </ul>
 * Tudo é formulário comum, sem JavaScript, e funciona só com teclado. Rotas [PROP], menos a
 * raiz, que é da arquitetura.
 */
@Controller
public class ManualController {

	private static final String TELA_BUSCA = "portaria/manual-busca";
	private static final String TELA_FORMULARIO = "portaria/manual";
	private static final String TELA_REVISAO = "portaria/manual-revisar";
	private static final String FORMULARIO = "manual";
	private static final String CAMPO_HORARIO = "ocorridoEm";

	/** O valor do campo {@code datetime-local}, sem segundos. */
	private static final DateTimeFormatter CAMPO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
	private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");
	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

	private final BuscaDeAlunos busca;

	private final RegistroManual registroManual;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	public ManualController(BuscaDeAlunos busca, RegistroManual registroManual, Clock relogio,
			FurinProperties propriedades) {
		this.busca = busca;
		this.registroManual = registroManual;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
	}

	/** Espaços nas pontas não contam. */
	@InitBinder(FORMULARIO)
	void limparEspacos(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
	}

	@GetMapping("/portaria/manual")
	public String buscar(@RequestParam(required = false) String termo, Model model) {
		model.addAttribute("termo", termo == null ? "" : termo.strip());
		if (termo != null) {
			Optional<String> problema = busca.problemaNoTermo(termo);
			if (problema.isPresent()) {
				model.addAttribute("erroDoTermo", problema.get());
			} else {
				model.addAttribute("resultado", busca.buscar(termo, Escopo.SO_ATIVOS));
				model.addAttribute("maxResultados", busca.maxResultados());
			}
		}
		return TELA_BUSCA;
	}

	@GetMapping("/portaria/manual/{idAluno}")
	public String formulario(@PathVariable long idAluno, Model model) {
		Ficha ficha = ficha(idAluno);
		ManualForm form = new ManualForm();
		// RF12, passo 4: o momento atual, que o funcionário pode alterar
		form.setOcorridoEm(CAMPO.format(LocalDateTime.ofInstant(relogio.instant(), fusoDaEscola)));
		model.addAttribute(FORMULARIO, form);
		return telaDoFormulario(ficha, model);
	}

	@PostMapping("/portaria/manual/{idAluno}/revisar")
	public String revisar(@PathVariable long idAluno, @Valid @ModelAttribute(FORMULARIO) ManualForm form,
			BindingResult erros, Model model) {
		Ficha ficha = ficha(idAluno);
		if (!ficha.podeRegistrar() || ficha.liberacaoPendente()) {
			return telaDoFormulario(ficha, model);
		}
		Optional<Instant> ocorrido = horario(form, erros);
		ocorrido.flatMap(instante -> registroManual.problemaNoHorario(ficha, instante, relogio.instant()))
				.ifPresent(problema -> rejeitarHorario(problema, ficha, erros));
		if (erros.hasErrors()) {
			return telaDoFormulario(ficha, model);
		}
		form.setTipoMostrado(ficha.tipo().name());
		form.setChaveRequisicao(UUID.randomUUID().toString());
		model.addAttribute("ficha", ficha);
		model.addAttribute("horarioInformado", DIA_E_HORA.format(ocorrido.orElseThrow().atZone(fusoDaEscola)));
		return TELA_REVISAO;
	}

	@PostMapping("/portaria/manual/{idAluno}/corrigir")
	public String corrigir(@PathVariable long idAluno, @ModelAttribute(FORMULARIO) ManualForm form, Model model) {
		return telaDoFormulario(ficha(idAluno), model);
	}

	@PostMapping("/portaria/manual/{idAluno}")
	public String confirmar(@PathVariable long idAluno, @Valid @ModelAttribute(FORMULARIO) ManualForm form,
			BindingResult erros, Authentication funcionario, Model model, RedirectAttributes redirecionamento) {
		Optional<Instant> ocorrido = horario(form, erros);
		Optional<TipoMovimentacao> tipoMostrado = tipoMostrado(form);
		Optional<UUID> chave = chave(form);
		if (erros.hasErrors() || tipoMostrado.isEmpty() || chave.isEmpty()) {
			// Só chega aqui um envio que não veio da revisão: volta ao formulário
			return telaDoFormulario(ficha(idAluno), model);
		}
		try {
			Registrado registrado = registroManual.registrar(idAluno, ocorrido.get(), form.getMotivo(),
					tipoMostrado.get(), chave.get(), funcionario.getName());
			redirecionamento.addFlashAttribute("registrado", mensagemDeSucesso(registrado));
			return "redirect:/portaria/manual";
		} catch (RegistroManualRecusado recusa) {
			if (recusa.getMotivo() == Motivo.ALUNO_NAO_ENCONTRADO) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND);
			}
			Ficha ficha = recusa.getFicha();
			if (recusa.getMotivo() == Motivo.SITUACAO_MUDOU) {
				// RF12, exceções: a tela volta para a revisão, com o tipo recalculado
				erros.reject("situacao", "A situação de " + ficha.primeiroNome() + " mudou. Revise o registro.");
			} else if (recusa.getMotivo() == Motivo.HORARIO_FUTURO || recusa.getMotivo() == Motivo.HORARIO_ANTERIOR) {
				rejeitarHorario(recusa.getMotivo(), ficha, erros);
			}
			return telaDoFormulario(ficha, model);
		}
	}

	// --- Apoio ---

	private String telaDoFormulario(Ficha ficha, Model model) {
		model.addAttribute("ficha", ficha);
		if (!ficha.podeRegistrar()) {
			// RF12, exceções [PROP: texto]
			model.addAttribute("bloqueio", "Não é possível registrar movimentação para este aluno.");
		} else if (ficha.liberacaoPendente()) {
			model.addAttribute("bloqueio", "Há uma liberação em andamento para " + ficha.primeiroNome()
					+ ". Aguarde a passagem ou cancele a liberação.");
		}
		return TELA_FORMULARIO;
	}

	/** O horário do campo, no fuso da escola (RN10), ou o erro no campo. */
	private Optional<Instant> horario(ManualForm form, BindingResult erros) {
		if (erros.hasFieldErrors(CAMPO_HORARIO)) {
			return Optional.empty();
		}
		try {
			return Optional.of(LocalDateTime.parse(form.getOcorridoEm(), CAMPO).atZone(fusoDaEscola).toInstant());
		} catch (DateTimeParseException invalido) {
			erros.rejectValue(CAMPO_HORARIO, "formato", "Informe a data e a hora no formato dia/mês/ano e hora:minuto.");
			return Optional.empty();
		}
	}

	/** RF12, exceções: as mensagens do horário. */
	private void rejeitarHorario(Motivo problema, Ficha ficha, BindingResult erros) {
		if (problema == Motivo.HORARIO_FUTURO) {
			erros.rejectValue(CAMPO_HORARIO, "futuro", "O horário não pode ser posterior ao horário atual.");
		} else {
			erros.rejectValue(CAMPO_HORARIO, "anterior", "O horário precisa ser posterior à última movimentação "
					+ "registrada (" + DIA_MES.format(ficha.ultimaConfirmada().atZone(fusoDaEscola)) + " às "
					+ HORA.format(ficha.ultimaConfirmada().atZone(fusoDaEscola)) + ").");
		}
	}

	private static Optional<TipoMovimentacao> tipoMostrado(ManualForm form) {
		try {
			return Optional.of(TipoMovimentacao.valueOf(form.getTipoMostrado()));
		} catch (IllegalArgumentException | NullPointerException invalido) {
			return Optional.empty();
		}
	}

	private static Optional<UUID> chave(ManualForm form) {
		try {
			return Optional.of(UUID.fromString(form.getChaveRequisicao()));
		} catch (IllegalArgumentException | NullPointerException invalida) {
			return Optional.empty();
		}
	}

	/** RF12, passo 7: "Entrada manual registrada para Ana às 07:15." De outro dia, com a data [PROP]. */
	private String mensagemDeSucesso(Registrado registrado) {
		String tipo = registrado.tipo() == TipoMovimentacao.SAIDA ? "Saída" : "Entrada";
		LocalDateTime naEscola = LocalDateTime.ofInstant(registrado.ocorridoEm(), fusoDaEscola);
		boolean hoje = naEscola.toLocalDate().equals(LocalDate.ofInstant(relogio.instant(), fusoDaEscola));
		String quando = (hoje ? "" : " em " + DIA_MES.format(naEscola)) + " às " + HORA.format(naEscola);
		return tipo + " manual registrada para " + registrado.primeiroNome() + quando + ".";
	}

	private Ficha ficha(long idAluno) {
		return registroManual.ficha(idAluno).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

}
