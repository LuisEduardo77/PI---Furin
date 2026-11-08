package br.com.furin.access.autenticacao;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Pedido automático da tela não é atividade do funcionário (T-34; RF16, passo 4; RF01,
 * fluxo A2). A tela Presentes pede os números de novo a cada 30 s, e todo pedido renova a
 * sessão no servidor: sem este filtro, uma tela Presentes aberta nunca deixaria a sessão
 * expirar por inatividade (T-16).
 * <p>
 * Cada pedido comum grava a hora da última atividade na sessão e devolve o tempo de
 * inatividade completo ({@code furin.sessao.inatividade}). Um pedido com o cabeçalho
 * {@value #CABECALHO} não muda essa hora: a sessão fica só com o tempo que faltava, contado
 * da última atividade. Se esse tempo já acabou, a sessão é encerrada e o pedido vai para o
 * login com o aviso de sessão expirada.
 */
public class AtualizacaoAutomatica extends OncePerRequestFilter {

	/** O cabeçalho que static/js/presentes.js põe na atualização automática. [PROP: nome] */
	public static final String CABECALHO = "X-Furin-Atualizacao-Automatica";

	static final String ULTIMA_ATIVIDADE = AtualizacaoAutomatica.class.getName() + ".ULTIMA_ATIVIDADE";

	private final TemposDaSessao tempos;

	private final Clock relogio;

	private final String paginaDoAviso;

	/** @param paginaDoAviso o login com o aviso, por exemplo {@code /login?expirada} */
	public AtualizacaoAutomatica(TemposDaSessao tempos, Clock relogio, String paginaDoAviso) {
		this.tempos = tempos;
		this.relogio = relogio;
		this.paginaDoAviso = paginaDoAviso;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
			throws ServletException, IOException {
		HttpSession sessao = pedido.getSession(false);
		if (sessao != null) {
			Instant agora = relogio.instant();
			Instant ultima = sessao.getAttribute(ULTIMA_ATIVIDADE) instanceof Instant instante ? instante : null;
			long inatividade = tempos.inatividadeEmSegundos();
			if (pedido.getHeader(CABECALHO) == null || ultima == null) {
				sessao.setAttribute(ULTIMA_ATIVIDADE, agora);
				sessao.setMaxInactiveInterval(Math.toIntExact(inatividade));
			} else {
				// Arredondado para baixo: o servidor nunca vai além do aviso da tela (sessao.js)
				long restante = (inatividade * 1000 - Duration.between(ultima, agora).toMillis()) / 1000;
				if (restante <= 0) {
					// Zero, para o Tomcat, seria "nunca expira": a sessão é encerrada aqui
					sessao.invalidate();
					resposta.sendRedirect(pedido.getContextPath() + paginaDoAviso);
					return;
				}
				sessao.setMaxInactiveInterval(Math.toIntExact(restante));
			}
		}
		cadeia.doFilter(pedido, resposta);
	}

}
