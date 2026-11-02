package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Bloqueio do login (T-14; RF02, CA-RF02-03 a 05 e 08). O número de tentativas e a
 * duração vêm de {@code furin.login.*}, e o tempo passa pelo relógio do teste, sem
 * esperar de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class BloqueioDeLoginTests {

	private static final String SENHA_ATE = "descartavel-ate-T14";
	private static final String SENHA_ERRADA = "senha-errada-T14";

	private static final String MENSAGEM_BLOQUEIO = "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente.";

	/** Substitui o bean "relogio" (RelogioConfig): o teste decide quando o tempo passa. */
	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(Instant.parse("2026-11-02T12:00:00Z"));
	}

	/** Para conferir que a tentativa bloqueada não consulta a conta. */
	@MockitoSpyBean
	private UsuarioRepository usuarios;

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
	private FurinProperties propriedades;

	private int maxTentativas;

	private Duration bloqueio;

	@BeforeEach
	void preparar() {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, 0, NULL, ?)",
				"ATE Demo", "ate.demo", encoder.encode(SENHA_ATE), SegurancaConfig.PERFIL_FUNCIONARIO,
				LocalDateTime.now(ZoneOffset.UTC));
		maxTentativas = propriedades.login().maxTentativas();
		bloqueio = propriedades.login().bloqueio();
		tentativas.esquecerTodos();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
	}

	// --- Bloqueio (CA-RF02-03) ---

	@Test
	void depoisDoMaximoDeFalhasASenhaCertaEhRecusada() throws Exception {
		errar("ate.demo", maxTentativas);

		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
	}

	@Test
	void umaFalhaAMenosQueOMaximoAindaDeixaEntrar() throws Exception {
		errar("ate.demo", maxTentativas - 1);

		entrarComSucesso("ate.demo");
	}

	@Test
	void duranteOBloqueioATelaMostraAMensagemE2() throws Exception {
		errar("ate.demo", maxTentativas);

		HtmlPage pagina = enviarFormulario("ate.demo", SENHA_ATE);

		assertThat(pagina.getUrl().getPath()).isEqualTo("/login");
		DomElement alerta = pagina.getElementById("erro-bloqueio");
		assertThat(alerta.getAttribute("role")).isEqualTo("alert");
		assertThat(alerta.getTextContent()).contains("Erro:", MENSAGEM_BLOQUEIO);
		assertThat(pagina.getElementById("erro-login")).isNull();
		assertThat(((HtmlInput) pagina.getElementById("usuario")).getValue()).isEqualTo("ate.demo");
		assertThat(((HtmlInput) pagina.getElementById("senha")).getValue()).isEmpty();
		assertThat(pagina.getWebResponse().getContentAsString()).doesNotContain(SENHA_ATE);
	}

	@Test
	void aUltimaFalhaPermitidaAindaMostraAMensagemGenerica() throws Exception {
		// RF02: a falha que bloqueia mostra E1; o aviso E2 vem na tentativa seguinte (CA-RF02-03)
		errar("ate.demo", maxTentativas - 1);

		HtmlPage pagina = enviarFormulario("ate.demo", SENHA_ERRADA);

		assertThat(pagina.getElementById("erro-login")).isNotNull();
		assertThat(pagina.getElementById("erro-bloqueio")).isNull();
	}

	@Test
	void tentativaBloqueadaNaoConsultaAConta() throws Exception {
		errar("ate.demo", maxTentativas);
		clearInvocations(usuarios);

		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(unauthenticated());

		verify(usuarios, never()).findByLogin(anyString());
	}

	@Test
	void variacoesDeCaixaEEspacosContamParaOMesmoLogin() throws Exception {
		// O contador usa o login normalizado, como a busca da conta (RF01, passo 3)
		for (int i = 0; i < maxTentativas; i++) {
			String variacao = i % 2 == 0 ? " ATE.Demo " : "ate.DEMO";
			mvc.perform(formLogin().user(variacao).password(SENHA_ERRADA)).andExpect(unauthenticated());
		}

		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(unauthenticated());
	}

	@Test
	void bloqueioDeUmLoginNaoAfetaOutro() throws Exception {
		errar("ninguem.teste", maxTentativas);

		entrarComSucesso("ate.demo");
	}

	// --- Desbloqueio (CA-RF02-04) ---

	@Test
	void passadoOBloqueioASenhaCertaVoltaAEntrar() throws Exception {
		errar("ate.demo", maxTentativas);

		avancar(bloqueio.minusSeconds(1));
		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(unauthenticated());

		avancar(Duration.ofSeconds(1));
		entrarComSucesso("ate.demo");
	}

	@Test
	void tentativaDuranteOBloqueioNaoProlongaOBloqueio() throws Exception {
		errar("ate.demo", maxTentativas);

		avancar(bloqueio.dividedBy(2));
		errar("ate.demo", 1);
		avancar(bloqueio.minus(bloqueio.dividedBy(2)));

		entrarComSucesso("ate.demo");
	}

	@Test
	void depoisDoBloqueioOContadorRecomeca() throws Exception {
		errar("ate.demo", maxTentativas);
		avancar(bloqueio);

		// Uma falha logo depois do desbloqueio não bloqueia de novo [PROP]
		errar("ate.demo", 1);
		entrarComSucesso("ate.demo");
	}

	// --- Login com sucesso zera o contador (CA-RF02-05) ---

	@Test
	void loginComSucessoZeraOContador() throws Exception {
		errar("ate.demo", maxTentativas - 1);
		entrarComSucesso("ate.demo");

		errar("ate.demo", maxTentativas - 1);
		entrarComSucesso("ate.demo");
	}

	@Test
	void loginCertoDepoisDoDesbloqueioZeraOContador() throws Exception {
		errar("ate.demo", maxTentativas);
		avancar(bloqueio);
		entrarComSucesso("ate.demo");

		errar("ate.demo", maxTentativas - 1);
		entrarComSucesso("ate.demo");
	}

	// --- Logins inexistentes (CA-RF02-08) ---

	@Test
	void loginInexistenteEhBloqueadoIgualComAMesmaTela() throws Exception {
		errar("ate.demo", maxTentativas);
		errar("ninguem.teste", maxTentativas);

		String existente = telaSemOQueVariaPorPedido(enviarFormulario("ate.demo", SENHA_ATE));
		navegador.getCookieManager().clearCookies();
		String inexistente = telaSemOQueVariaPorPedido(enviarFormulario("ninguem.teste", SENHA_ATE));

		assertThat(existente).contains(MENSAGEM_BLOQUEIO);
		assertThat(inexistente).isEqualTo(existente);
	}

	@Test
	void loginInexistenteTambemDesbloqueiaNoMesmoTempo() throws Exception {
		errar("ninguem.teste", maxTentativas);
		avancar(bloqueio.minusSeconds(1));
		assertThat(tentativas.bloqueado("ninguem.teste")).isTrue();

		avancar(Duration.ofSeconds(1));
		assertThat(tentativas.bloqueado("ninguem.teste")).isFalse();
	}

	// --- Log de auditoria (RF02, passo 4; A10 §8.15) ---

	@Test
	void bloqueioEAsTentativasBloqueadasVaoParaOLogSemASenha(CapturedOutput saida) throws Exception {
		Instant inicio = relogio.instant();
		errar("ate.demo", maxTentativas);
		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(unauthenticated());

		assertThat(linhas(saida, "evento=LOGIN_BLOQUEADO")).singleElement().asString()
				.contains("usuario=a***", "ate=" + inicio.plus(bloqueio), "origem=127.0.0.1")
				.doesNotContain("ate.demo");
		assertThat(linhas(saida, "motivo=BLOQUEADO")).singleElement().asString()
				.contains("evento=LOGIN_FALHA", "usuario=a***");
		assertThat(linhas(saida, "motivo=CREDENCIAL_INVALIDA")).hasSize(maxTentativas);
		assertThat(saida.getAll()).doesNotContain(SENHA_ATE).doesNotContain(SENHA_ERRADA);
	}

	private void errar(String login, int vezes) throws Exception {
		for (int i = 0; i < vezes; i++) {
			mvc.perform(formLogin().user(login).password(SENHA_ERRADA)).andExpect(unauthenticated());
		}
	}

	private void entrarComSucesso(String login) throws Exception {
		mvc.perform(formLogin().user(login).password(SENHA_ATE))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL))
				.andExpect(authenticated().withUsername(login));
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

	private HtmlPage enviarFormulario(String login, String senha) throws Exception {
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

	private static List<String> linhas(CapturedOutput saida, String trecho) {
		return saida.getOut().lines().filter(linha -> linha.contains(trecho)).toList();
	}

	/** Relógio em UTC que só anda quando o teste manda. */
	static final class RelogioAjustavel extends Clock {

		private volatile Instant agora;

		RelogioAjustavel(Instant inicio) {
			this.agora = inicio;
		}

		void avancar(Duration tempo) {
			agora = agora.plus(tempo);
		}

		@Override
		public Instant instant() {
			return agora;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zona) {
			throw new UnsupportedOperationException("O relógio do teste fica sempre em UTC");
		}

	}

}
