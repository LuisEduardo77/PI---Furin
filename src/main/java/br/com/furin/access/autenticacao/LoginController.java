package br.com.furin.access.autenticacao;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Tela "Login | Furin Access" (T-12; RF01; A11 §5.9.18). Só o GET: o POST /login é
 * tratado pelo Spring Security (SegurancaConfig).
 */
@Controller
public class LoginController {

	@GetMapping("/login")
	public String exibir(Authentication autenticacao) {
		// Quem já entrou não precisa ver o login de novo
		if (autenticacao != null && autenticacao.isAuthenticated()
				&& !(autenticacao instanceof AnonymousAuthenticationToken)) {
			return "redirect:" + SegurancaConfig.TELA_INICIAL;
		}
		return "autenticacao/login";
	}

}
