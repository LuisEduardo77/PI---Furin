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
 * Aceite obrigatório dos termos (DEC-47): enquanto o funcionário não aceitar o Termo de uso e
 * a Política de privacidade, <b>qualquer</b> rota leva à tela do aceite, menos a própria tela,
 * o "Sair" e os arquivos estáticos. Mesmo modelo da troca obrigatória da senha
 * ({@link TrocaDeSenhaObrigatoria}): a pendência fica num atributo da sessão, gravado no login
 * ({@link LoginComSucessoAuditado}) e apagado no aceite ({@link TermosController}).
 * <p>
 * Fica depois da troca de senha: com as duas pendentes, a senha vem primeiro, e o aceite em
 * seguida. Antes da autorização, para que até uma rota negada leve ao aceite.
 */
public class AceiteDosTermosObrigatorio extends OncePerRequestFilter {

	/** Rota da tela do aceite. [PROP: rota] */
	public static final String TELA_DO_ACEITE = "/termos/aceite";

	/** Atributo da sessão que marca o aceite pendente. */
	static final String ACEITE_PENDENTE = AceiteDosTermosObrigatorio.class.getName() + ".PENDENTE";

	private final RequestMatcher liberadas;

	/** @param outrasRotasLiberadas além da própria tela: o "Sair" e os arquivos estáticos */
	public AceiteDosTermosObrigatorio(String... outrasRotasLiberadas) {
		PathPatternRequestMatcher.Builder rotas = PathPatternRequestMatcher.withDefaults();
		this.liberadas = new OrRequestMatcher(rotas.matcher(TELA_DO_ACEITE), new OrRequestMatcher(
				Arrays.stream(outrasRotasLiberadas).map(rota -> (RequestMatcher) rotas.matcher(rota)).toList()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		// A troca de senha pendente tem a vez: o filtro dela leva a /senha/definir
		if (aceitePendente(pedido) && !TrocaDeSenhaObrigatoria.trocaPendente(pedido) && !liberadas.matches(pedido)) {
			resposta.sendRedirect(pedido.getContextPath() + TELA_DO_ACEITE);
			return;
		}
		cadeia.doFilter(pedido, resposta);
	}

	static boolean aceitePendente(HttpServletRequest pedido) {
		HttpSession sessao = pedido.getSession(false);
		return sessao != null && Boolean.TRUE.equals(sessao.getAttribute(ACEITE_PENDENTE));
	}

	static void marcarPendente(HttpServletRequest pedido) {
		pedido.getSession().setAttribute(ACEITE_PENDENTE, Boolean.TRUE);
	}

	static void concluir(HttpServletRequest pedido) {
		HttpSession sessao = pedido.getSession(false);
		if (sessao != null) {
			sessao.removeAttribute(ACEITE_PENDENTE);
		}
	}

}
