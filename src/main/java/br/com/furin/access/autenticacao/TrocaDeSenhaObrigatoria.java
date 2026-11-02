package br.com.furin.access.autenticacao;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Troca obrigatória da senha temporária (T-15; RF01, fluxo A1, passo 5; CA-RF01-04):
 * enquanto a troca não acontecer, <b>qualquer</b> rota leva a "Definir nova senha", menos
 * a própria tela, o "Sair" e os arquivos estáticos que a tela usa.
 * <p>
 * A pendência fica num atributo da sessão, gravado no login (LoginComSucessoAuditado) e
 * apagado na troca (SenhaController). Fica antes da autorização (SegurancaConfig), para
 * que até uma rota negada leve à troca.
 */
public class TrocaDeSenhaObrigatoria extends OncePerRequestFilter {

	/** Rota da tela "Definir nova senha" (arquitetura §4). */
	public static final String TELA_DE_TROCA = "/senha/definir";

	/** Atributo da sessão que marca a troca pendente. */
	static final String TROCA_PENDENTE = TrocaDeSenhaObrigatoria.class.getName() + ".PENDENTE";

	private final RequestMatcher liberadas;

	/** @param outrasRotasLiberadas além da própria tela: o "Sair" e os arquivos estáticos */
	public TrocaDeSenhaObrigatoria(String... outrasRotasLiberadas) {
		PathPatternRequestMatcher.Builder rotas = PathPatternRequestMatcher.withDefaults();
		this.liberadas = new OrRequestMatcher(rotas.matcher(TELA_DE_TROCA), new OrRequestMatcher(
				Arrays.stream(outrasRotasLiberadas).map(rota -> (RequestMatcher) rotas.matcher(rota)).toList()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		if (trocaPendente(pedido) && !liberadas.matches(pedido)) {
			resposta.sendRedirect(pedido.getContextPath() + TELA_DE_TROCA);
			return;
		}
		cadeia.doFilter(pedido, resposta);
	}

	static boolean trocaPendente(HttpServletRequest pedido) {
		HttpSession sessao = pedido.getSession(false);
		return sessao != null && Boolean.TRUE.equals(sessao.getAttribute(TROCA_PENDENTE));
	}

	static void marcarPendente(HttpServletRequest pedido) {
		pedido.getSession().setAttribute(TROCA_PENDENTE, Boolean.TRUE);
	}

	static void concluir(HttpServletRequest pedido) {
		HttpSession sessao = pedido.getSession(false);
		if (sessao != null) {
			sessao.removeAttribute(TROCA_PENDENTE);
		}
	}

}
