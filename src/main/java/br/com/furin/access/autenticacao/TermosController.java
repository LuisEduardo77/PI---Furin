package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.SegurancaConfig;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tela do aceite do Termo de uso e da Política de privacidade (DEC-47), no primeiro acesso e
 * depois de cada nova versão. Os textos são os mesmos das janelas da tela de login
 * ({@code templates/termos/textos.html}). O aceite grava a versão na conta e registra
 * {@code TERMOS_ACEITOS} no log de auditoria, só com a versão.
 */
@Controller
public class TermosController {

	private static final String PARA_A_TELA_INICIAL = "redirect:" + SegurancaConfig.TELA_INICIAL;

	private final AceiteDosTermos aceite;

	private final Auditoria auditoria;

	public TermosController(AceiteDosTermos aceite, Auditoria auditoria) {
		this.aceite = aceite;
		this.auditoria = auditoria;
	}

	@GetMapping(AceiteDosTermosObrigatorio.TELA_DO_ACEITE)
	public String exibir(HttpServletRequest pedido, Model model) {
		if (!AceiteDosTermosObrigatorio.aceitePendente(pedido)) {
			return PARA_A_TELA_INICIAL;
		}
		model.addAttribute("versaoDosTermos", aceite.versaoAtual());
		return "termos/aceite";
	}

	@PostMapping(AceiteDosTermosObrigatorio.TELA_DO_ACEITE)
	public String aceitar(Authentication autenticacao, HttpServletRequest pedido,
			RedirectAttributes redirecionamento) {
		if (!AceiteDosTermosObrigatorio.aceitePendente(pedido)) {
			return PARA_A_TELA_INICIAL;
		}
		aceite.aceitar(autenticacao.getName());
		auditoria.registrar(EventoAuditoria.TERMOS_ACEITOS, detalhes().texto("versao", aceite.versaoAtual()));
		AceiteDosTermosObrigatorio.concluir(pedido);
		redirecionamento.addFlashAttribute("termosAceitos", true);
		return PARA_A_TELA_INICIAL;
	}

}
