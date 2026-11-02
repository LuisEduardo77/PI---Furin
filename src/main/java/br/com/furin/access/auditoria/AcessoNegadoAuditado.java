package br.com.furin.access.auditoria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Registra {@link EventoAuditoria#ACESSO_NEGADO} em toda resposta 403 do Spring Security,
 * com a rota e o usuário mascarado, e depois responde 403 como o padrão
 * (RF03, CA-RF03-05). O 403 padrão chama a página de erro do Spring Boot, que mostra
 * templates/error/403.html, a página acessível do CA-RF03-06 (T-17).
 */
@Component
public class AcessoNegadoAuditado implements AccessDeniedHandler {

	private final Auditoria auditoria;
	private final AccessDeniedHandler padrao = new AccessDeniedHandlerImpl();

	public AcessoNegadoAuditado(Auditoria auditoria) {
		this.auditoria = auditoria;
	}

	@Override
	public void handle(HttpServletRequest pedido, HttpServletResponse resposta, AccessDeniedException negacao)
			throws IOException, ServletException {
		// Só o caminho: a query string fica de fora, porque pode ter dados digitados
		auditoria.registrar(EventoAuditoria.ACESSO_NEGADO, detalhes()
				.texto("metodo", pedido.getMethod())
				.texto("rota", pedido.getRequestURI())
				.texto("motivo", negacao instanceof CsrfException ? "CSRF" : "AUTORIZACAO"));
		padrao.handle(pedido, resposta, negacao);
	}

}
