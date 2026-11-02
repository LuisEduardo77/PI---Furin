package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Login negado (RF02, fluxo principal, passo 3). Registra {@code LOGIN_FALHA} com o
 * login normalizado e mascarado, o motivo e a origem, <b>sem a senha</b> (CA-RF02-06), e
 * volta à tela com a mesma mensagem para usuário inexistente, senha errada e conta
 * inativa (A10 §8.5; CA-RF02-01, CA-RF02-02).
 * <p>
 * O motivo fica só no log: {@code CREDENCIAL_INVALIDA} junta usuário inexistente e senha
 * errada (o Spring Security não distingue os dois), e {@code CONTA_INATIVA} vale para a
 * conta com {@code ativo = 0}.
 */
public class LoginComFalhaAuditado implements AuthenticationFailureHandler {

	private final Auditoria auditoria;

	private final TentativasDeLogin tentativas;

	public LoginComFalhaAuditado(Auditoria auditoria, TentativasDeLogin tentativas) {
		this.auditoria = auditoria;
		this.tentativas = tentativas;
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest pedido, HttpServletResponse resposta,
			AuthenticationException excecao) throws IOException {
		String login = ContasDosFuncionarios.normalizarLogin(
				pedido.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY));
		auditoria.registrarComo(EventoAuditoria.LOGIN_FALHA, login, detalhes()
				.texto("motivo", motivo(excecao))
				.texto("origem", pedido.getRemoteAddr()));
		// RF02, passos 3.1 e 4 (T-14): conta a falha e, na última permitida, bloqueia
		Instant bloqueadoAte = tentativas.registrarFalha(login);
		if (bloqueadoAte != null) {
			auditoria.registrarComo(EventoAuditoria.LOGIN_BLOQUEADO, login, detalhes()
					.texto("ate", bloqueadoAte.toString())
					.texto("origem", pedido.getRemoteAddr()));
		}
		// Esta falha mostra a mensagem genérica; o aviso de bloqueio vem na tentativa seguinte (E2)
		RetornoAoLogin.voltar(pedido, resposta, Map.of(RetornoAoLogin.ERRO_CREDENCIAL, true));
	}

	private static String motivo(AuthenticationException excecao) {
		return switch (excecao) {
			case DisabledException inativa -> "CONTA_INATIVA";
			case BadCredentialsException credencial -> "CREDENCIAL_INVALIDA";
			default -> "OUTRO";
		};
	}

}
