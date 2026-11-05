package br.com.furin.access.config;

import java.util.function.Supplier;

import org.springframework.http.server.RequestPath;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * A última regra da autorização (DEC-42; RF03, exceções): vale para tudo o que não está
 * nas listas de {@link SegurancaConfig}.
 * <ul>
 * <li>Rota com controller e sem regra: negada, 403 (CA-RF03-04).</li>
 * <li>Rota que não existe, com um funcionário logado: segue, e a resposta é 404.</li>
 * <li>Sem login, ou sem o perfil: negada. Quem não entrou vai ao login e não descobre
 * quais rotas existem.</li>
 * </ul>
 */
class NegarRotaExistenteSemRegra implements AuthorizationManager<RequestAuthorizationContext> {

	private final AuthorityAuthorizationManager<RequestAuthorizationContext> funcionario = AuthorityAuthorizationManager
			.hasRole(SegurancaConfig.PERFIL_FUNCIONARIO);

	private final RequestMappingHandlerMapping rotas;

	NegarRotaExistenteSemRegra(RequestMappingHandlerMapping rotas) {
		this.rotas = rotas;
	}

	@Override
	public AuthorizationResult authorize(Supplier<? extends Authentication> autenticacao,
			RequestAuthorizationContext contexto) {
		AuthorizationResult perfil = funcionario.authorize(autenticacao, contexto);
		boolean ehFuncionario = perfil != null && perfil.isGranted();
		return new AuthorizationDecision(ehFuncionario && !rotaExiste(contexto.getRequest()));
	}

	/** Na dúvida, a rota existe, e o pedido é negado. */
	private boolean rotaExiste(HttpServletRequest pedido) {
		RequestPath anterior = ServletRequestPathUtils.hasParsedRequestPath(pedido)
				? ServletRequestPathUtils.getParsedRequestPath(pedido)
				: null;
		ServletRequestPathUtils.parseAndCache(pedido);
		try {
			return rotas.getHandler(pedido) != null;
		} catch (Exception metodoNaoAceitoNaRota) {
			return true;
		} finally {
			ServletRequestPathUtils.setParsedRequestPath(anterior, pedido);
		}
	}

}
