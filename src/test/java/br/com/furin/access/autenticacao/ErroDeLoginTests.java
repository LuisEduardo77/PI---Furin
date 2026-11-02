package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
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
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Erros do login (T-13; RF02, menos o bloqueio, que é da T-14). As contas são
 * inseridas em cada teste, com senhas descartáveis, e somem no rollback.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ErroDeLoginTests {

	private static final String SENHA_ATE = "descartavel-ate-T13";
	private static final String SENHA_INATIVA = "descartavel-inativa-T13";
	private static final String SENHA_ERRADA = "senha-errada-T13";

	private static final String MENSAGEM_GENERICA = "Usuário ou senha inválidos. Confira os dados e tente novamente.";

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

	/** Para conferir que um campo vazio não consulta a conta (RF02, E3). */
	@MockitoSpyBean
	private UsuarioRepository usuarios;

	@BeforeEach
	void prepararContas() {
		conta("ate.demo", "ATE Demo", SENHA_ATE, true);
		conta("inativa.teste", "Conta Inativa", SENHA_INATIVA, false);
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
		// O contador de falhas fica em memória e dura entre os testes (T-14)
		tentativas.esquecerTodos();
	}

	/** As três falhas do E1: senha errada, login inexistente e conta inativa com a senha certa. */
	static Stream<Arguments> credenciaisInvalidas() {
		return Stream.of(
				Arguments.of("ate.demo", SENHA_ERRADA, "a***", "CREDENCIAL_INVALIDA"),
				Arguments.of("ninguem.teste", SENHA_ERRADA, "n***", "CREDENCIAL_INVALIDA"),
				Arguments.of("inativa.teste", SENHA_INATIVA, "i***", "CONTA_INATIVA"));
	}

	// --- E1: mensagem única (CA-RF02-01, CA-RF02-02) ---

	@ParameterizedTest
	@MethodSource("credenciaisInvalidas")
	void falhaVoltaAoLoginComOMesmoCodigoHttp(String login, String senha) throws Exception {
		mvc.perform(formLogin().user(login).password(senha))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
		// Controle do E3: numa tentativa de verdade, a conta é consultada
		verify(usuarios).findByLogin(login);
	}

	@Test
	void senhaErradaLoginInexistenteEContaInativaMostramAMesmaTela() throws Exception {
		// CA-RF02-01 e CA-RF02-02: a mesma tela, a não ser o usuário digitado
		String senhaErrada = telaSemOQueVariaPorPedido(entrar("ate.demo", SENHA_ERRADA));
		navegador.getCookieManager().clearCookies();
		String inexistente = telaSemOQueVariaPorPedido(entrar("ninguem.teste", SENHA_ERRADA));
		navegador.getCookieManager().clearCookies();
		String inativa = telaSemOQueVariaPorPedido(entrar("inativa.teste", SENHA_INATIVA));

		assertThat(senhaErrada).contains(MENSAGEM_GENERICA);
		assertThat(inexistente).isEqualTo(senhaErrada);
		assertThat(inativa).isEqualTo(senhaErrada);
	}

	@Test
	void mensagemGenericaEhAnunciadaEApontadaPelosCampos() throws Exception {
		// CA-RF02-07; A11 §5.9.9: role="alert", aria-invalid, aria-describedby e foco no 1º campo
		HtmlPage pagina = entrar("ate.demo", SENHA_ERRADA);

		assertThat(pagina.getWebResponse().getStatusCode()).isEqualTo(200);
		assertThat(pagina.getTitleText()).isEqualTo("Login | Furin Access");
		DomElement alerta = pagina.getElementById("erro-login");
		assertThat(alerta.getAttribute("role")).isEqualTo("alert");
		assertThat(alerta.getTextContent()).contains("Erro:", MENSAGEM_GENERICA);

		HtmlInput usuario = (HtmlInput) pagina.getElementById("usuario");
		HtmlInput senha = (HtmlInput) pagina.getElementById("senha");
		for (HtmlInput campo : new HtmlInput[] { usuario, senha }) {
			assertThat(campo.getAttribute("aria-invalid")).isEqualTo("true");
			assertThat(campo.getAttribute("aria-describedby")).isEqualTo("erro-login");
		}
		assertThat(usuario.hasAttribute("autofocus")).isTrue();
		assertThat(senha.hasAttribute("autofocus")).isFalse();
	}

	@Test
	void usuarioDigitadoContinuaESenhaVoltaVazia() throws Exception {
		// CA-RF02-07: o usuário volta como foi digitado; a senha nunca volta
		HtmlPage pagina = entrar(" ATE.Demo ", SENHA_ERRADA);

		assertThat(((HtmlInput) pagina.getElementById("usuario")).getValue()).isEqualTo(" ATE.Demo ");
		assertThat(((HtmlInput) pagina.getElementById("senha")).getValue()).isEmpty();
		assertThat(pagina.getWebResponse().getContentAsString()).doesNotContain(SENHA_ERRADA);
	}

	@Test
	void mensagemNaoFicaDepoisDeRecarregar() throws Exception {
		// O erro vale só para a volta do POST; recarregar mostra a tela limpa
		entrar("ate.demo", SENHA_ERRADA);
		HtmlPage recarregada = navegador.getPage("http://localhost/login");

		assertThat(recarregada.getElementById("erro-login")).isNull();
		assertThat(((HtmlInput) recarregada.getElementById("usuario")).getValue()).isEmpty();
	}

	@Test
	void depoisDoErroOLoginCertoEntra() throws Exception {
		HtmlPage erro = entrar("ate.demo", SENHA_ERRADA);
		((HtmlInput) erro.getElementById("senha")).type(SENHA_ATE);
		HtmlPage portaria = ((HtmlButton) erro.querySelector("main form button[type=submit]")).click();

		assertThat(portaria.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
	}

	// --- Log de auditoria (CA-RF02-06; A10 §8.15) ---

	@ParameterizedTest
	@MethodSource("credenciaisInvalidas")
	void cadaFalhaVaiParaOLogSemASenha(String login, String senha, String mascarado, String motivo,
			CapturedOutput saida) throws Exception {
		mvc.perform(formLogin().user(login).password(senha)).andExpect(unauthenticated());
		mvc.perform(formLogin().user(login).password(senha)).andExpect(unauthenticated());

		assertThat(linhasDeFalha(saida)).hasSize(2).allSatisfy(linha -> assertThat(linha)
				.contains("usuario=" + mascarado, "motivo=" + motivo, "origem=127.0.0.1", "quando=")
				.doesNotContain(login));
		assertThat(saida.getAll()).doesNotContain(senha);
	}

	@Test
	void loginDaFalhaVaiNormalizadoEMascarado(CapturedOutput saida) throws Exception {
		mvc.perform(formLogin().user("  Ninguem.Teste  ").password(SENHA_ERRADA)).andExpect(unauthenticated());

		assertThat(linhasDeFalha(saida)).singleElement().asString().contains("usuario=n***");
	}

	// --- E3: campo vazio, sem consultar a conta ---

	@ParameterizedTest
	@ValueSource(strings = { "", "   " })
	void usuarioVazioPedeOUsuarioSemConsultarAConta(String vazio, CapturedOutput saida) throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", vazio).param("password", SENHA_ATE))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());

		verify(usuarios, never()).findByLogin(anyString());
		assertThat(linhasDeFalha(saida)).isEmpty();
	}

	@Test
	void senhaVaziaPedeASenhaSemConsultarAConta() throws Exception {
		mvc.perform(post("/login").with(csrf()).param("username", "ate.demo").param("password", ""))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());

		verify(usuarios, never()).findByLogin(anyString());
	}

	@Test
	void usuarioVazioMostraInformeOUsuario() throws Exception {
		HtmlPage pagina = entrar("", SENHA_ATE);

		DomElement erro = pagina.getElementById("usuario-erro");
		assertThat(erro.getAttribute("role")).isEqualTo("alert");
		assertThat(erro.getTextContent()).contains("Erro:", "Informe o usuário.");
		HtmlInput usuario = (HtmlInput) pagina.getElementById("usuario");
		assertThat(usuario.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(usuario.getAttribute("aria-describedby")).isEqualTo("usuario-erro");
		assertThat(usuario.hasAttribute("autofocus")).isTrue();

		HtmlInput senha = (HtmlInput) pagina.getElementById("senha");
		assertThat(senha.hasAttribute("aria-invalid")).isFalse();
		assertThat(senha.getValue()).isEmpty();
		assertThat(pagina.getElementById("senha-erro")).isNull();
		assertThat(pagina.getElementById("erro-login")).isNull();
	}

	@Test
	void senhaVaziaMostraInformeASenhaEGuardaOUsuario() throws Exception {
		HtmlPage pagina = entrar("ate.demo", "");

		DomElement erro = pagina.getElementById("senha-erro");
		assertThat(erro.getAttribute("role")).isEqualTo("alert");
		assertThat(erro.getTextContent()).contains("Erro:", "Informe a senha.");
		HtmlInput senha = (HtmlInput) pagina.getElementById("senha");
		assertThat(senha.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(senha.getAttribute("aria-describedby")).isEqualTo("senha-erro");
		assertThat(senha.hasAttribute("autofocus")).isTrue();

		HtmlInput usuario = (HtmlInput) pagina.getElementById("usuario");
		assertThat(usuario.getValue()).isEqualTo("ate.demo");
		assertThat(usuario.hasAttribute("aria-invalid")).isFalse();
		assertThat(usuario.hasAttribute("autofocus")).isFalse();
	}

	@Test
	void doisCamposVaziosMostramAsDuasMensagensEOFocoVaiParaOPrimeiro() throws Exception {
		HtmlPage pagina = entrar("", "");

		assertThat(pagina.getElementById("usuario-erro").getTextContent()).contains("Informe o usuário.");
		assertThat(pagina.getElementById("senha-erro").getTextContent()).contains("Informe a senha.");
		assertThat(pagina.getElementById("usuario").hasAttribute("autofocus")).isTrue();
		assertThat(pagina.getElementById("senha").hasAttribute("autofocus")).isFalse();
	}

	@Test
	void campoVazioSemTokenCsrfEhRecusado() throws Exception {
		// A validação fica depois do CSRF: sem o token, nada chega a ela
		mvc.perform(post("/login").param("username", "").param("password", ""))
				.andExpect(status().isForbidden());
	}

	@Test
	void telaSemErroNaoMarcaNenhumCampo() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");

		assertThat(pagina.querySelectorAll("[role=alert], [aria-invalid], [aria-describedby], [autofocus]")).isEmpty();
	}

	private HtmlPage entrar(String login, String senha) throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).setValue(login);
		((HtmlInput) pagina.getElementById("senha")).setValue(senha);
		return ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
	}

	/** O HTML do conteúdo principal sem o token CSRF (muda a cada pedido) e sem o usuário digitado. */
	private static String telaSemOQueVariaPorPedido(HtmlPage pagina) {
		pagina.querySelector("input[name=_csrf]").remove();
		((HtmlInput) pagina.getElementById("usuario")).removeAttribute("value");
		return pagina.getTitleText() + pagina.getWebResponse().getStatusCode()
				+ pagina.querySelector("main").asXml();
	}

	private static List<String> linhasDeFalha(CapturedOutput saida) {
		return saida.getOut().lines().filter(linha -> linha.contains("evento=LOGIN_FALHA")).toList();
	}

	private void conta(String login, String nome, String senha, boolean ativa) {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, ?, 0, NULL, ?)",
				nome, login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, ativa,
				LocalDateTime.now(ZoneOffset.UTC));
	}

}
