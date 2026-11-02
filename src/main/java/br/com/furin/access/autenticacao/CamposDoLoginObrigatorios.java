package br.com.furin.access.autenticacao;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Preenchimento do login (RF02, E3): com o usuário ou a senha vazios, volta à tela com
 * "Informe o usuário." / "Informe a senha." <b>sem consultar a conta</b>.
 * <p>
 * Fica logo antes do filtro de login do Spring Security, depois do CSRF
 * (SegurancaConfig). Não é uma tentativa de autenticação: não vai para o log como
 * {@code LOGIN_FALHA} nem conta para o bloqueio (T-14).
 */
public class CamposDoLoginObrigatorios extends OncePerRequestFilter {

	private final RequestMatcher postDoLogin = PathPatternRequestMatcher.withDefaults()
			.matcher(HttpMethod.POST, RetornoAoLogin.PAGINA_DE_LOGIN);

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		if (postDoLogin.matches(pedido)) {
			// Só espaços também é vazio: o login é normalizado (RF01, passo 3)
			boolean usuarioVazio = ContasDosFuncionarios.normalizarLogin(
					pedido.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY))
					.isEmpty();
			// A senha não é normalizada: só a senha sem nenhum caractere é vazia
			String senha = pedido.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY);
			boolean senhaVazia = senha == null || senha.isEmpty();
			if (usuarioVazio || senhaVazia) {
				RetornoAoLogin.voltar(pedido, resposta, Map.of(
						RetornoAoLogin.ERRO_USUARIO_VAZIO, usuarioVazio,
						RetornoAoLogin.ERRO_SENHA_VAZIA, senhaVazia));
				return;
			}
		}
		cadeia.doFilter(pedido, resposta);
	}

}
