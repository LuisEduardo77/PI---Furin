package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSessionEvent;

/**
 * Expiração da sessão por inatividade (T-16; RF01, fluxo A2; CA-RF01-10): os tempos de
 * {@code furin.sessao.*}, a volta ao login com o aviso, a renovação e a marcação do
 * aviso no layout. O aviso funcionando no navegador fica em {@link AvisoDeSessaoTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SessaoTests {

	private static final String SENHA = "descartavel-ate-T16";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WebClient navegador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private TentativasDeLogin tentativas;

	@Autowired
	private TemposDaSessao tempos;

	@Autowired
	private FurinProperties propriedades;

	@BeforeEach
	void preparar() {
		conta("ate.demo", false);
		tentativas.esquecerTodos();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
	}

	// --- Tempos (furin.sessao.*) ---

	@Test
	void temposVemDasPropriedades() {
		assertThat(tempos.inatividadeEmSegundos()).isEqualTo(propriedades.sessao().inatividade().toSeconds());
		assertThat(tempos.avisoAntesEmSegundos()).isEqualTo(propriedades.sessao().avisoAntes().toSeconds());
	}

	@Test
	void cadaSessaoNovaExpiraDepoisDaInatividade() {
		// Em segundos, sem o arredondamento para minutos do server.servlet.session.timeout
		MockHttpSession sessao = new MockHttpSession();
		tempos.sessionCreated(new HttpSessionEvent(sessao));

		assertThat(sessao.getMaxInactiveInterval()).isEqualTo((int) propriedades.sessao().inatividade().toSeconds());
	}

	// --- Sessão expirada (A2, passo 3) ---

	@ParameterizedTest
	@ValueSource(strings = { "/portaria", "/alunos", "/senha/alterar", "/login" })
	void pedidoComASessaoExpiradaLevaAoLoginComOAviso(String rota) throws Exception {
		mvc.perform(get(rota).with(sessaoExpirada())).andExpect(redirectedUrl(SegurancaConfig.LOGIN_SESSAO_EXPIRADA));
	}

	@Test
	void formularioEnviadoComASessaoExpiradaTambemLevaAoLogin() throws Exception {
		// O token CSRF era da sessão que acabou: sem ele, o pedido também vai para o aviso
		mvc.perform(post("/senha/alterar").with(sessaoExpirada()).param("senhaAtual", SENHA))
				.andExpect(redirectedUrl(SegurancaConfig.LOGIN_SESSAO_EXPIRADA));
	}

	@Test
	void semSessaoAnteriorOLoginNaoMostraOAviso() throws Exception {
		mvc.perform(get("/portaria")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void telaDeLoginMostraOAvisoDeSessaoEncerrada() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost" + SegurancaConfig.LOGIN_SESSAO_EXPIRADA);

		DomElement aviso = pagina.getElementById("aviso-expirada");
		assertThat(aviso.getAttribute("role")).isEqualTo("status");
		assertThat(aviso.getTextContent()).contains("Sua sessão foi encerrada por inatividade. Entre novamente.");
		assertThat(pagina.getTitleText()).isEqualTo("Login | Furin Access");
	}

	@Test
	void sairApagaOCookieDaSessao() throws Exception {
		// Sem o cookie antigo, a tela de "você saiu" não vira o aviso de sessão expirada
		mvc.perform(post("/logout").session(entrar()).with(csrf()))
				.andExpect(redirectedUrl("/login?logout"))
				.andExpect(cookie().maxAge("JSESSIONID", 0));
	}

	@Test
	void depoisDeSairATelaMostraQueSaiuENaoQueExpirou() throws Exception {
		HtmlPage portaria = entrarPeloFormulario();
		HtmlPage login = ((HtmlButton) portaria.querySelector("nav form button")).click();

		assertThat(login.getElementById("aviso-expirada")).isNull();
		assertThat(login.querySelector("[role=status]").getTextContent()).contains("você saiu do sistema");
	}

	// --- "Continuar conectado" (A2, passo 2) ---

	@Test
	void continuarConectadoRenovaASessao() throws Exception {
		mvc.perform(post(SessaoController.RENOVAR).session(entrar()).with(csrf())).andExpect(status().isNoContent());
	}

	@Test
	void continuarConectadoSemOTokenCsrfEhRecusado() throws Exception {
		// Como no navegador: a página já mostrou o token, que ficou guardado na sessão
		MockHttpSession sessao = entrar();
		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao)).andExpect(status().isOk());

		mvc.perform(post(SessaoController.RENOVAR).session(sessao)).andExpect(status().isForbidden());
	}

	@Test
	void continuarConectadoSemLoginLevaAoLogin() throws Exception {
		mvc.perform(post(SessaoController.RENOVAR).with(csrf())).andExpect(redirectedUrl("/login"));
	}

	@Test
	void continuarConectadoFuncionaComATrocaDeSenhaPendente() throws Exception {
		// A tela "Definir nova senha" também tem o aviso
		conta("nova.conta", true);
		MockHttpSession sessao = (MockHttpSession) mvc.perform(formLogin().user("nova.conta").password(SENHA))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA))
				.andReturn().getRequest().getSession(false);

		mvc.perform(post(SessaoController.RENOVAR).session(sessao).with(csrf())).andExpect(status().isNoContent());
	}

	// --- Marcação do aviso no layout (A11 §5.16.2; DEC-40) ---

	@Test
	void telaDeQuemEntrouTemOAvisoComOsTemposDoServidor() throws Exception {
		HtmlPage portaria = entrarPeloFormulario();

		DomElement aviso = portaria.getElementById("aviso-sessao");
		assertThat(aviso.getTagName()).isEqualTo("dialog");
		assertThat(aviso.getAttribute("role")).isEqualTo("alertdialog");
		assertThat(aviso.hasAttribute("open")).isFalse();
		assertThat(aviso.getAttribute("data-inatividade")).isEqualTo(Long.toString(tempos.inatividadeEmSegundos()));
		assertThat(aviso.getAttribute("data-aviso")).isEqualTo(Long.toString(tempos.avisoAntesEmSegundos()));
		assertThat(aviso.getAttribute("data-renovar")).isEqualTo(SessaoController.RENOVAR);
		assertThat(aviso.getAttribute("data-login")).isEqualTo(SegurancaConfig.LOGIN_SESSAO_EXPIRADA);

		DomElement titulo = portaria.getElementById(aviso.getAttribute("aria-labelledby"));
		DomElement texto = portaria.getElementById(aviso.getAttribute("aria-describedby"));
		assertThat(titulo.getTextContent()).isEqualTo("Sua sessão vai expirar");
		assertThat(texto.getTextContent()).contains("a sessão será encerrada em", "se você não usar o sistema");
		assertThat(aviso.querySelector("button[type=button]").getTextContent()).isEqualTo("Continuar conectado");

		assertThat(portaria.querySelectorAll("script[src='/js/sessao.js']")).hasSize(1);
		assertThat(portaria.<DomElement>querySelector("meta[name=_csrf]").getAttribute("content")).isNotBlank();
		assertThat(portaria.querySelectorAll("script:not([src])")).isEmpty();
	}

	@Test
	void telaDeLoginNaoTemOAviso() throws Exception {
		HtmlPage login = navegador.getPage("http://localhost/login");

		assertThat(login.getElementById("aviso-sessao")).isNull();
		assertThat(login.querySelectorAll("meta[name=_csrf]")).isEmpty();
	}

	// --- Apoio ---

	private MockHttpSession entrar() throws Exception {
		return (MockHttpSession) mvc.perform(formLogin().user("ate.demo").password(SENHA))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	private HtmlPage entrarPeloFormulario() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).setValue("ate.demo");
		((HtmlInput) pagina.getElementById("senha")).setValue(SENHA);
		return ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
	}

	/** Como o navegador depois da inatividade: manda o cookie de uma sessão que já não existe. */
	private static RequestPostProcessor sessaoExpirada() {
		return pedido -> {
			pedido.setCookies(new Cookie("JSESSIONID", "sessao-que-expirou"));
			pedido.setRequestedSessionId("sessao-que-expirou");
			pedido.setRequestedSessionIdValid(false);
			pedido.setRequestedSessionIdFromCookie(true);
			return pedido;
		};
	}

	private void conta(String login, boolean trocaObrigatoria) {
		LocalDateTime agora = LocalDateTime.now(ZoneOffset.UTC);
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, ?, ?, ?)",
				"Conta Teste", login, encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO, trocaObrigatoria,
				trocaObrigatoria ? agora.plusDays(1) : null, agora);
	}

}
