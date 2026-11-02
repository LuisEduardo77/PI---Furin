package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Login com as contas do banco e tela inicial (T-12; RF01, RF03). As contas são
 * inseridas em cada teste, com senhas descartáveis, e somem no rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class LoginTests {

	private static final String SENHA_ATE = "descartavel-ate-T12";
	private static final String SENHA_GESTAO = "descartavel-gestao-T12";
	private static final String SENHA_INATIVA = "descartavel-inativa-T12";

	private static final List<String> MENU_DO_RF03 = List.of("Portaria", "Alunos", "Presentes", "Histórico",
			"Contas da equipe", "Alterar senha", "Sair");

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

	@BeforeEach
	void prepararContas() {
		conta("ate.demo", "ATE Demo", SENHA_ATE, true);
		conta("gestao.demo", "Gestão Demo", SENHA_GESTAO, true);
		conta("inativa.teste", "Conta Inativa", SENHA_INATIVA, false);
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
		// O contador de falhas fica em memória e dura entre os testes (T-14)
		tentativas.esquecerTodos();
	}

	static Stream<Arguments> contasDeDemonstracao() {
		return Stream.of(Arguments.of("ate.demo", SENHA_ATE), Arguments.of("gestao.demo", SENHA_GESTAO));
	}

	// --- Autenticação (RF01) ---

	@ParameterizedTest
	@MethodSource("contasDeDemonstracao")
	void contaDeDemonstracaoEntraECaiNaPortaria(String login, String senha) throws Exception {
		// CA-RF01-01, CA-RF03-01
		mvc.perform(formLogin().user(login).password(senha))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL))
				.andExpect(authenticated().withUsername(login).withRoles(SegurancaConfig.PERFIL_FUNCIONARIO));
	}

	@Test
	void loginAceitaVariacoesDeCaixaEEspacosNasPontas() throws Exception {
		// CA-RF01-02: " ATE.Demo " = "ate.demo"
		mvc.perform(formLogin().user(" ATE.Demo ").password(SENHA_ATE))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL))
				.andExpect(authenticated().withUsername("ate.demo"));
	}

	@Test
	void senhaNaoEhNormalizada() throws Exception {
		mvc.perform(formLogin().user("ate.demo").password(" " + SENHA_ATE.toUpperCase() + " "))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
	}

	@Test
	void senhaErradaNaoEntra() throws Exception {
		mvc.perform(formLogin().user("ate.demo").password("senha-errada-T12"))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
	}

	@Test
	void contaInativaNaoEntra() throws Exception {
		// Parte do CA-RF01-03; a mensagem igual à de credencial inválida está em ErroDeLoginTests
		mvc.perform(formLogin().user("inativa.teste").password(SENHA_INATIVA))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
	}

	@Test
	void depoisDoLoginVaiParaAPortariaMesmoVindoDeOutraRota() throws Exception {
		MockHttpSession sessao = new MockHttpSession();
		mvc.perform(get("/alunos").session(sessao)).andExpect(redirectedUrl("/login"));

		mvc.perform(post("/login").session(sessao).with(csrf())
				.param("username", "gestao.demo").param("password", SENHA_GESTAO))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	@Test
	void sessaoMudaDeIdentificadorNoLogin() throws Exception {
		// CA-RF01-07: proteção contra fixação de sessão
		MockHttpSession sessao = new MockHttpSession();
		String antes = sessao.getId();

		mvc.perform(post("/login").session(sessao).with(csrf()).with(cookieDaSessao(antes))
				.param("username", "ate.demo").param("password", SENHA_ATE))
				.andExpect(authenticated());

		assertThat(sessao.getId()).isNotEqualTo(antes);
	}

	@Test
	void loginComSucessoVaiParaOLogSemSenha(CapturedOutput saida) throws Exception {
		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(authenticated());

		assertThat(saida.getOut()).contains("evento=LOGIN_SUCESSO", "usuario=a***");
		assertThat(saida.getAll()).doesNotContain(SENHA_ATE);
	}

	@Test
	void quemJaEntrouEhLevadoDoLoginParaAPortaria() throws Exception {
		mvc.perform(get("/login").with(user("ate.demo").roles(SegurancaConfig.PERFIL_FUNCIONARIO)))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	// --- Tela de login (CA-RF01-09, CA-RF01-11; A11 §5.9.18) ---

	@Test
	void telaDeLoginTemTituloIdiomaEPularParaOConteudo() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");

		assertThat(pagina.getTitleText()).isEqualTo("Login | Furin Access");
		assertThat(pagina.getDocumentElement().getAttribute("lang")).isEqualTo("pt-BR");
		HtmlElement primeiro = pagina.getTabbableElements().getFirst();
		assertThat(primeiro.getTextContent().trim()).isEqualTo("Ir para o conteúdo principal");
		assertThat(pagina.getElementsByTagName("h1")).hasSize(1);
	}

	@Test
	void telaDeLoginNaoMostraOMenu() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		assertThat(pagina.querySelectorAll("nav")).isEmpty();
	}

	@Test
	void camposTemRotuloEAutocompleteEAceitamColar() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");

		HtmlInput usuario = (HtmlInput) pagina.getElementById("usuario");
		HtmlInput senha = (HtmlInput) pagina.getElementById("senha");
		assertThat(pagina.querySelector("label[for='usuario']").getTextContent()).contains("Usuário");
		assertThat(pagina.querySelector("label[for='senha']").getTextContent()).contains("Senha");
		assertThat(usuario.getNameAttribute()).isEqualTo("username");
		assertThat(usuario.getAttribute("autocomplete")).isEqualTo("username");
		assertThat(senha.getNameAttribute()).isEqualTo("password");
		assertThat(senha.getTypeAttribute()).isEqualTo("password");
		assertThat(senha.getAttribute("autocomplete")).isEqualTo("current-password");
		// Colar permitido: nenhum bloqueio de paste nem de copiar/arrastar
		assertThat(pagina.querySelectorAll("[onpaste], [oncopy], [oncut], [ondrop]")).isEmpty();
		assertThat(pagina.getElementsByTagName("script")).isEmpty();
	}

	@Test
	void formularioDeLoginTemTokenCsrf() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		HtmlForm formulario = (HtmlForm) pagina.querySelector("main form");
		assertThat(formulario.getActionAttribute()).isEqualTo("/login");
		assertThat(formulario.getMethodAttribute()).isEqualToIgnoringCase("post");
		assertThat(formulario.getInputByName("_csrf").getValue()).isNotBlank();
	}

	// --- Tela inicial e menu (RF03) ---

	@Test
	void ateEGestaoEntramPeloFormularioEVeemOMesmoMenu() throws Exception {
		// CA-RF03-01 e CA-RF03-02, pelo formulário, como no navegador
		HtmlPage portariaAte = entrar("ate.demo", SENHA_ATE);
		navegador.getCookieManager().clearCookies();
		HtmlPage portariaGestao = entrar(" Gestao.Demo ", SENHA_GESTAO);

		for (HtmlPage portaria : List.of(portariaAte, portariaGestao)) {
			assertThat(portaria.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
			assertThat(portaria.getTitleText()).isEqualTo("Portaria | Furin Access");
			assertThat(itensDoMenu(portaria)).containsExactlyElementsOf(MENU_DO_RF03);
			assertThat(portaria.querySelector("nav a[aria-current='page']").getTextContent().trim())
					.isEqualTo("Portaria");
		}
		assertThat(portariaAte.querySelector("main strong").getTextContent()).isEqualTo("ate.demo");
		assertThat(portariaGestao.querySelector("main strong").getTextContent()).isEqualTo("gestao.demo");
	}

	@Test
	void sairVoltaAoLoginEAPortariaPedeLoginDeNovo() throws Exception {
		// RF01, passo 8; CA-RF01-08
		HtmlPage portaria = entrar("ate.demo", SENHA_ATE);

		HtmlPage login = ((HtmlButton) portaria.querySelector("nav form button")).click();

		assertThat(login.getUrl().toString()).endsWith("/login?logout");
		assertThat(login.getTitleText()).isEqualTo("Login | Furin Access");
		assertThat(login.querySelector("[role=status]").getTextContent()).contains("você saiu do sistema");

		HtmlPage depois = navegador.getPage("http://localhost/portaria");
		assertThat(depois.getUrl().getPath()).isEqualTo("/login");
		assertThat(depois.getTitleText()).isEqualTo("Login | Furin Access");
	}

	private HtmlPage entrar(String login, String senha) throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).type(login);
		((HtmlInput) pagina.getElementById("senha")).type(senha);
		return ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
	}

	/** Como o navegador: o pedido chega com o cookie da sessão já existente. */
	private static RequestPostProcessor cookieDaSessao(String id) {
		return pedido -> {
			pedido.setRequestedSessionId(id);
			pedido.setRequestedSessionIdValid(true);
			pedido.setRequestedSessionIdFromCookie(true);
			return pedido;
		};
	}

	private static List<String> itensDoMenu(HtmlPage pagina) {
		DomElement menu = pagina.querySelector("nav[aria-label='Menu principal']");
		return menu.querySelectorAll("a, button").stream().map(item -> item.getTextContent().trim()).toList();
	}

	private void conta(String login, String nome, String senha, boolean ativa) {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, ?, 0, NULL, ?)",
				nome, login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, ativa,
				LocalDateTime.now(ZoneOffset.UTC));
	}

}
