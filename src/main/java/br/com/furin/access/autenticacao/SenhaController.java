package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Instant;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Telas de senha do funcionário (T-15; RF01; RN14):
 * <ul>
 * <li>{@code /senha/definir}: troca obrigatória da senha temporária (fluxo A1);</li>
 * <li>{@code /senha/alterar}: "Alterar senha", que exige a senha atual (fluxo A3).</li>
 * </ul>
 * O tamanho mínimo vem de {@code furin.senha.tamanho-minimo} (DEC-28, E4). As senhas
 * digitadas nunca voltam para a tela nem vão para o log: o evento {@code SENHA_ALTERADA}
 * leva só o login mascarado e o fluxo.
 */
@Controller
public class SenhaController {

	private static final String TELA_DEFINIR = "senha/definir";
	private static final String TELA_ALTERAR = "senha/alterar";
	private static final String CAMPO_ATUAL = "senhaAtual";
	private static final String CAMPO_NOVA = "novaSenha";
	private static final String CAMPO_CONFIRMACAO = "confirmacao";

	private final SenhasDosFuncionarios senhas;

	private final ContasDosFuncionarios contas;

	private final TentativasDeLogin tentativas;

	private final Auditoria auditoria;

	private final int tamanhoMinimo;

	public SenhaController(SenhasDosFuncionarios senhas, ContasDosFuncionarios contas, TentativasDeLogin tentativas,
			Auditoria auditoria, FurinProperties propriedades) {
		this.senhas = senhas;
		this.contas = contas;
		this.tentativas = tentativas;
		this.auditoria = auditoria;
		this.tamanhoMinimo = propriedades.senha().tamanhoMinimo();
	}

	@ModelAttribute("tamanhoMinimo")
	int tamanhoMinimo() {
		return tamanhoMinimo;
	}

	// --- Fluxo A1: troca obrigatória ---

	@GetMapping(TrocaDeSenhaObrigatoria.TELA_DE_TROCA)
	public String exibirDefinir(HttpServletRequest pedido, Model model) {
		if (!TrocaDeSenhaObrigatoria.trocaPendente(pedido)) {
			return "redirect:" + SegurancaConfig.TELA_INICIAL;
		}
		model.addAttribute("troca", new DefinirSenhaForm());
		return TELA_DEFINIR;
	}

	@PostMapping(TrocaDeSenhaObrigatoria.TELA_DE_TROCA)
	public String definir(@Valid @ModelAttribute("troca") DefinirSenhaForm form, BindingResult erros,
			Authentication autenticacao, HttpServletRequest pedido, RedirectAttributes redirecionamento)
			throws ServletException {
		if (!TrocaDeSenhaObrigatoria.trocaPendente(pedido)) {
			return "redirect:" + SegurancaConfig.TELA_INICIAL;
		}
		Usuario conta = senhas.conta(autenticacao.getName());
		if (contas.senhaTemporariaVencida(conta)) {
			// A1, passo 2: a temporária venceu durante a sessão. Sai e orienta (CA-RF01-05)
			pedido.logout();
			redirecionamento.addFlashAttribute(RetornoAoLogin.ERRO_SENHA_VENCIDA, true);
			return "redirect:" + RetornoAoLogin.PAGINA_DE_LOGIN;
		}
		conferirNovaSenha(form.getNovaSenha(), form.getConfirmacao(), conta, erros,
				"A nova senha precisa ter pelo menos " + tamanhoMinimo
						+ " caracteres e ser diferente da senha temporária.");
		if (erros.hasErrors()) {
			return TELA_DEFINIR;
		}
		trocar(autenticacao.getName(), form.getNovaSenha(), "DEFINICAO", pedido);
		TrocaDeSenhaObrigatoria.concluir(pedido);
		redirecionamento.addFlashAttribute("senhaDefinida", true);
		return "redirect:" + SegurancaConfig.TELA_INICIAL;
	}

	// --- Fluxo A3: alterar a própria senha ---

	@GetMapping("/senha/alterar")
	public String exibirAlterar(Model model) {
		model.addAttribute("alteracao", new AlterarSenhaForm());
		return TELA_ALTERAR;
	}

	@PostMapping("/senha/alterar")
	public String alterar(@Valid @ModelAttribute("alteracao") AlterarSenhaForm form, BindingResult erros,
			Authentication autenticacao, HttpServletRequest pedido, RedirectAttributes redirecionamento) {
		String login = autenticacao.getName();
		if (tentativas.bloqueado(login)) {
			// RF02: com o login bloqueado, a senha atual nem é conferida
			erros.reject("bloqueado", "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente.");
			return TELA_ALTERAR;
		}
		Usuario conta = senhas.conta(login);
		if (!erros.hasFieldErrors(CAMPO_ATUAL) && !senhas.ehASenhaAtual(conta, form.getSenhaAtual())) {
			erros.rejectValue(CAMPO_ATUAL, "naoConfere", "A senha atual não confere.");
			registrarSenhaAtualErrada(login, pedido);
		}
		conferirNovaSenha(form.getNovaSenha(), form.getConfirmacao(), conta, erros,
				"A nova senha precisa ter pelo menos " + tamanhoMinimo
						+ " caracteres e ser diferente da senha atual.");
		if (erros.hasErrors()) {
			return TELA_ALTERAR;
		}
		trocar(login, form.getNovaSenha(), "ALTERACAO", pedido);
		redirecionamento.addFlashAttribute("senhaAlterada", true);
		return "redirect:/senha/alterar";
	}

	/**
	 * Regras da nova senha (A1, passo 2; A3, passo 3; DEC-28, E4): pelo menos
	 * {@code furin.senha.tamanho-minimo} caracteres, diferente da senha atual da conta
	 * (a temporária, no fluxo A1) e igual à confirmação.
	 */
	private void conferirNovaSenha(String nova, String confirmacao, Usuario conta, BindingResult erros,
			String mensagemDaRegra) {
		if (erros.hasFieldErrors(CAMPO_NOVA)) {
			return;
		}
		if (nova.codePointCount(0, nova.length()) < tamanhoMinimo || senhas.ehASenhaAtual(conta, nova)) {
			erros.rejectValue(CAMPO_NOVA, "regra", mensagemDaRegra);
			return;
		}
		if (!erros.hasFieldErrors(CAMPO_CONFIRMACAO) && !nova.equals(confirmacao)) {
			erros.rejectValue(CAMPO_CONFIRMACAO, "diferente", "A confirmação não é igual à nova senha.");
		}
	}

	/** Grava, cria uma nova sessão (A3, passo 4) e registra SENHA_ALTERADA, sem a senha. */
	private void trocar(String login, String novaSenha, String fluxo, HttpServletRequest pedido) {
		senhas.trocar(login, novaSenha);
		pedido.changeSessionId();
		auditoria.registrar(EventoAuditoria.SENHA_ALTERADA, detalhes().texto("fluxo", fluxo));
	}

	/** RF01, exceções: a senha atual errada conta como tentativa sem sucesso do RF02 [PROP]. */
	private void registrarSenhaAtualErrada(String login, HttpServletRequest pedido) {
		auditoria.registrarComo(EventoAuditoria.LOGIN_FALHA, login, detalhes()
				.texto("motivo", "SENHA_ATUAL_INCORRETA")
				.texto("origem", pedido.getRemoteAddr()));
		Instant bloqueadoAte = tentativas.registrarFalha(login);
		if (bloqueadoAte != null) {
			auditoria.registrarComo(EventoAuditoria.LOGIN_BLOQUEADO, login, detalhes()
					.texto("ate", bloqueadoAte.toString())
					.texto("origem", pedido.getRemoteAddr()));
		}
	}

	/** Fluxo A1. As mensagens de campo vazio são [PROP]. */
	public static class DefinirSenhaForm {

		@NotEmpty(message = "Informe a nova senha.")
		private String novaSenha;

		@NotEmpty(message = "Informe a confirmação da nova senha.")
		private String confirmacao;

		public String getNovaSenha() {
			return novaSenha;
		}

		public void setNovaSenha(String novaSenha) {
			this.novaSenha = novaSenha;
		}

		public String getConfirmacao() {
			return confirmacao;
		}

		public void setConfirmacao(String confirmacao) {
			this.confirmacao = confirmacao;
		}

	}

	/** Fluxo A3. As mensagens de campo vazio são [PROP]. */
	public static class AlterarSenhaForm extends DefinirSenhaForm {

		@NotEmpty(message = "Informe a senha atual.")
		private String senhaAtual;

		public String getSenhaAtual() {
			return senhaAtual;
		}

		public void setSenhaAtual(String senhaAtual) {
			this.senhaAtual = senhaAtual;
		}

	}

}
