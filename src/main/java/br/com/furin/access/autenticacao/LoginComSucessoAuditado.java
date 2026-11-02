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
 * Depois do login, zera o contador de falhas (RF02, passo 5), registra
 * {@code LOGIN_SUCESSO} (RF01, passo 6) e leva sempre à tela
 * inicial, a mesma para todos os funcionários (RF03; DEC-37), mesmo que o pedido
 * original tenha sido outra rota.
 */
public class LoginComSucessoAuditado extends SimpleUrlAuthenticationSuccessHandler {

	private final Auditoria auditoria;

	private final TentativasDeLogin tentativas;

	public LoginComSucessoAuditado(Auditoria auditoria, TentativasDeLogin tentativas, String telaInicial) {
		super(telaInicial);
		setAlwaysUseDefaultTargetUrl(true);
		this.auditoria = auditoria;
		this.tentativas = tentativas;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest pedido, HttpServletResponse resposta,
			Authentication autenticacao) throws IOException, ServletException {
		// RF02, passo 5 (T-14): o login com sucesso zera o contador de falhas
		tentativas.registrarSucesso(autenticacao.getName());
		auditoria.registrarComo(EventoAuditoria.LOGIN_SUCESSO, autenticacao.getName(), detalhes());
		super.onAuthenticationSuccess(pedido, resposta, autenticacao);
	}

}
