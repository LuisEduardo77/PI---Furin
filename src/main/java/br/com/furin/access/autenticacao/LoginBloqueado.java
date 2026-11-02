package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login bloqueado (T-14; RF02, passo 2 e E2): enquanto durar o bloqueio, a tentativa é
 * recusada <b>antes</b> de conferir a senha, mesmo que ela esteja certa (CA-RF02-03), e
 * sem consultar a conta. A resposta é a mesma para contas existentes e inexistentes
 * (CA-RF02-08), porque o bloqueio só depende do login digitado.
 * <p>
 * A tentativa recusada vai para o log como {@code LOGIN_FALHA} com
 * {@code motivo=BLOQUEADO}, sem a senha, e não prolonga o bloqueio [PROP]. Fica logo
 * antes do filtro de login do Spring Security, depois de CamposDoLoginObrigatorios
 * (SegurancaConfig).
 */
public class LoginBloqueado extends OncePerRequestFilter {

	private final RequestMatcher postDoLogin = PathPatternRequestMatcher.withDefaults()
			.matcher(HttpMethod.POST, RetornoAoLogin.PAGINA_DE_LOGIN);

	private final TentativasDeLogin tentativas;

	private final Auditoria auditoria;

	public LoginBloqueado(TentativasDeLogin tentativas, Auditoria auditoria) {
		this.tentativas = tentativas;
		this.auditoria = auditoria;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		if (postDoLogin.matches(pedido)) {
			String login = ContasDosFuncionarios.normalizarLogin(
					pedido.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY));
			if (tentativas.bloqueado(login)) {
				auditoria.registrarComo(EventoAuditoria.LOGIN_FALHA, login, detalhes()
						.texto("motivo", "BLOQUEADO")
						.texto("origem", pedido.getRemoteAddr()));
				RetornoAoLogin.voltar(pedido, resposta, Map.of(RetornoAoLogin.ERRO_BLOQUEADO, true));
				return;
			}
		}
		cadeia.doFilter(pedido, resposta);
	}

}
