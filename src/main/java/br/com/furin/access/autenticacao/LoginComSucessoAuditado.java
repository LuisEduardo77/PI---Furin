package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Depois do login, registra {@code LOGIN_SUCESSO} (RF01, passo 6) e leva sempre à tela
 * inicial, a mesma para todos os funcionários (RF03; DEC-37), mesmo que o pedido
 * original tenha sido outra rota.
 */
public class LoginComSucessoAuditado extends SimpleUrlAuthenticationSuccessHandler {

	private final Auditoria auditoria;

	public LoginComSucessoAuditado(Auditoria auditoria, String telaInicial) {
		super(telaInicial);
		setAlwaysUseDefaultTargetUrl(true);
		this.auditoria = auditoria;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest pedido, HttpServletResponse resposta,
			Authentication autenticacao) throws IOException, ServletException {
		auditoria.registrarComo(EventoAuditoria.LOGIN_SUCESSO, autenticacao.getName(), detalhes());
		super.onAuthenticationSuccess(pedido, resposta, autenticacao);
	}

}
