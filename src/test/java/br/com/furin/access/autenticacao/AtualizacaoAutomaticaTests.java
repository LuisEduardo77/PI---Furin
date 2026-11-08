package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import br.com.furin.access.RelogioAjustavel;

/**
 * A atualização automática da tela Presentes não prolonga a sessão (T-34; RF16, passo 4;
 * RF01, fluxo A2). Inatividade de 15 min, como em {@code furin.sessao.inatividade}.
 */
class AtualizacaoAutomaticaTests {

	private static final Instant INICIO = Instant.parse("2026-10-20T15:30:00Z");
	private static final Duration INATIVIDADE = Duration.ofMinutes(15);

	private RelogioAjustavel relogio;
	private AtualizacaoAutomatica filtro;
	private MockHttpSession sessao;

	@BeforeEach
	void preparar() {
		relogio = new RelogioAjustavel(INICIO);
		filtro = new AtualizacaoAutomatica(new TemposDaSessao(INATIVIDADE, Duration.ofMinutes(1)), relogio,
				"/login?expirada");
		sessao = new MockHttpSession();
	}

	@Test
	void pedidoComumDevolveAInatividadeCompleta() throws Exception {
		sessao.setMaxInactiveInterval(10);

		MockFilterChain cadeia = pedir(false);

		assertThat(cadeia.getRequest()).as("o pedido segue").isNotNull();
		assertThat(sessao.getMaxInactiveInterval()).isEqualTo(900);
		assertThat(sessao.getAttribute(AtualizacaoAutomatica.ULTIMA_ATIVIDADE)).isEqualTo(INICIO);
	}

	@Test
	void atualizacaoAutomaticaDeixaSoOTempoQueFaltavaDaUltimaAtividade() throws Exception {
		pedir(false);
		relogio.avancar(Duration.ofMinutes(10).plusMillis(500));

		MockFilterChain cadeia = pedir(true);

		assertThat(cadeia.getRequest()).isNotNull();
		// 15 min − 10,5 s arredondado para baixo: 299 s
		assertThat(sessao.getMaxInactiveInterval()).isEqualTo(299);
		assertThat(sessao.getAttribute(AtualizacaoAutomatica.ULTIMA_ATIVIDADE)).isEqualTo(INICIO);
	}

	@Test
	void atualizacaoAutomaticaDepoisDoFimDaInatividadeEncerraASessao() throws Exception {
		pedir(false);
		relogio.avancar(INATIVIDADE);

		MockHttpServletResponse resposta = new MockHttpServletResponse();
		MockFilterChain cadeia = pedir(true, resposta);

		assertThat(cadeia.getRequest()).as("o pedido não chega ao controller").isNull();
		assertThat(sessao.isInvalid()).isTrue();
		assertThat(resposta.getRedirectedUrl()).isEqualTo("/login?expirada");
	}

	@Test
	void pedidoComumDepoisDeAtualizacoesRecomecaAContagem() throws Exception {
		pedir(false);
		relogio.avancar(Duration.ofMinutes(5));
		pedir(true);
		relogio.avancar(Duration.ofMinutes(1));

		pedir(false);

		assertThat(sessao.getMaxInactiveInterval()).isEqualTo(900);
		assertThat(sessao.getAttribute(AtualizacaoAutomatica.ULTIMA_ATIVIDADE))
				.isEqualTo(INICIO.plus(Duration.ofMinutes(6)));
	}

	@Test
	void semSessaoNadaMuda() throws Exception {
		MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/login");
		MockFilterChain cadeia = new MockFilterChain();

		filtro.doFilter(pedido, new MockHttpServletResponse(), cadeia);

		assertThat(cadeia.getRequest()).isNotNull();
		assertThat(pedido.getSession(false)).isNull();
	}

	private MockFilterChain pedir(boolean automatico) throws Exception {
		return pedir(automatico, new MockHttpServletResponse());
	}

	private MockFilterChain pedir(boolean automatico, MockHttpServletResponse resposta) throws Exception {
		MockHttpServletRequest pedido = new MockHttpServletRequest("GET", "/presentes");
		pedido.setSession(sessao);
		if (automatico) {
			pedido.addHeader(AtualizacaoAutomatica.CABECALHO, "1");
		}
		MockFilterChain cadeia = new MockFilterChain();
		filtro.doFilter(pedido, resposta, cadeia);
		return cadeia;
	}

}
