package br.com.furin.access.autenticacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Conta desativada durante a sessão (T-17; RF03, exceções; CA-RF03-03): a cada pedido de
 * quem entrou, o servidor confere no banco se a conta continua ativa. Se não estiver, a
 * próxima requisição encerra a sessão, vai para o log como {@code ACESSO_NEGADO} com
 * {@code motivo=CONTA_DESATIVADA} e leva ao login com o aviso.
 * <p>
 * Fica antes da troca obrigatória de senha e da autorização (SegurancaConfig): nenhuma
 * rota, nem as liberadas durante a troca, passa com a conta desativada.
 */
public class ContaDesativada extends OncePerRequestFilter {

	private final ContasDosFuncionarios contas;

	private final Auditoria auditoria;

	private final String paginaDoAviso;

	/** @param paginaDoAviso o login com o aviso, por exemplo {@code /login?desativada} */
	public ContaDesativada(ContasDosFuncionarios contas, Auditoria auditoria, String paginaDoAviso) {
		this.contas = contas;
		this.auditoria = auditoria;
		this.paginaDoAviso = paginaDoAviso;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.isAuthenticated()
				&& !(autenticacao instanceof AnonymousAuthenticationToken)
				&& contas.contaDesativada(autenticacao.getName())) {
			// Só o caminho: a query string fica de fora, como em AcessoNegadoAuditado
			auditoria.registrar(EventoAuditoria.ACESSO_NEGADO, detalhes()
					.texto("metodo", pedido.getMethod())
					.texto("rota", pedido.getRequestURI())
					.texto("motivo", "CONTA_DESATIVADA"));
			new SecurityContextLogoutHandler().logout(pedido, resposta, autenticacao);
			// Sessão nova no lugar da encerrada: o cookie antigo não vira "sessão expirada"
			pedido.getSession(true);
			resposta.sendRedirect(pedido.getContextPath() + paginaDoAviso);
			return;
		}
		cadeia.doFilter(pedido, resposta);
	}

}
