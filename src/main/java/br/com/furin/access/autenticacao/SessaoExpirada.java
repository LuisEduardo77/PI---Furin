package br.com.furin.access.autenticacao;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sessão expirada por inatividade (T-16; RF01, fluxo A2, passo 3): o navegador mandou o
 * cookie de uma sessão que já não existe, então a próxima ação, GET ou POST, leva ao
 * login com o aviso.
 * <p>
 * Fica antes do CSRF (SegurancaConfig): um formulário enviado depois da expiração tem o
 * token da sessão antiga e, sem este filtro, receberia 403. Um pedido <b>sem</b> cookie de
 * sessão não passa por aqui, e um POST sem token continua recebendo 403 com
 * {@code ACESSO_NEGADO} no log. Não se usa o {@code invalidSessionUrl} do Spring
 * Security porque ele também redireciona esse POST sem sessão nenhuma.
 */
public class SessaoExpirada extends OncePerRequestFilter {

	private final String paginaDoAviso;

	/** @param paginaDoAviso o login com o aviso, por exemplo {@code /login?expirada} */
	public SessaoExpirada(String paginaDoAviso) {
		this.paginaDoAviso = paginaDoAviso;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		// Cookie de sessão que não vale mais e nenhuma sessão em uso neste pedido
		if (pedido.getRequestedSessionId() != null && !pedido.isRequestedSessionIdValid()
				&& pedido.getSession(false) == null) {
			// Sessão nova antes de redirecionar: o cookie antigo é trocado e não há laço
			pedido.getSession(true);
			resposta.sendRedirect(pedido.getContextPath() + paginaDoAviso);
			return;
		}
		cadeia.doFilter(pedido, resposta);
	}

}
