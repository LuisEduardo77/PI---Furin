package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Troca obrigatória da senha temporária, senha vencida e "Alterar senha" (T-15; RF01,
 * fluxos A1 e A3; RN14): CA-RF01-04, 05, 06, 09, 12 e 13. Os valores vêm de
 * {@code furin.senha.*}, e o tempo passa pelo relógio do teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SenhaTests {

	private static final String TEMPORARIA = "temporaria-T15";
	private static final String SENHA_ATE = "descartavel-ate-T15";
	private static final String NOVA = "nova-senha-escolhida-T15";
	private static final String ERRADA = "senha-errada-T15";

	private static final String VENCIDA = "Sua senha temporária expirou. Procure a escola para receber uma nova.";

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(Instant.parse("2026-11-02T12:00:00Z"));
	}

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

	private int tamanhoMinimo;

	private Duration validadeTemporaria;

	@BeforeEach
	void preparar() {
		tamanhoMinimo = propriedades.senha().tamanhoMinimo();
		validadeTemporaria = propriedades.senha().validadeTemporaria();
		// Conta nova, como a T-43 vai criar: temporária com prazo de furin.senha.validade-temporaria
		conta("nova.conta", TEMPORARIA, true, agora().plus(validadeTemporaria));
		conta("ate.demo", SENHA_ATE, false, null);
		tentativas.esquecerTodos();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
	}

	// --- Login com troca pendente (RF01, passo 5) ---

	@Test
	void contaComSenhaTemporariaVaiParaDefinirNovaSenha() throws Exception {
		mvc.perform(formLogin().user("nova.conta").password(TEMPORARIA))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA))
				.andExpect(authenticated().withUsername("nova.conta"));
	}

	@Test
	void contaSemTrocaPendenteContinuaIndoParaAPortaria() throws Exception {
		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	// --- CA-RF01-04: toda rota leva a "Definir nova senha" ---

	@ParameterizedTest
	@ValueSource(strings = { "/portaria", "/alunos", "/presentes", "/historico", "/contas/equipe", "/senha/alterar",
			"/exemplo/layout", "/login", "/rota-que-nao-existe" })
	void comATrocaPendenteTodaRotaLevaParaDefinir(String rota) throws Exception {
		MockHttpSession sessao = entrar("nova.conta", TEMPORARIA);

		mvc.perform(get(rota).session(sessao)).andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));
		if (!rota.equals("/login")) {
			// POST /login é uma nova tentativa de login, tratada antes pelos filtros do login
			mvc.perform(post(rota).session(sessao).with(csrf()))
					.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));
		}
	}

	@Test
	void comATrocaPendenteADefinicaoOSairEOsEstaticosFuncionam() throws Exception {
		MockHttpSession sessao = entrar("nova.conta", TEMPORARIA);

		mvc.perform(get(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao)).andExpect(status().isOk());
		mvc.perform(get("/css/base.css").session(sessao)).andExpect(status().isOk());
		mvc.perform(post("/logout").session(sessao).with(csrf())).andExpect(redirectedUrl("/login?logout"));
	}

	@Test
	void semTrocaPendenteADefinicaoVoltaParaAPortaria() throws Exception {
		MockHttpSession sessao = entrar("ate.demo", SENHA_ATE);

		mvc.perform(get(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
		mvc.perform(post(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao).with(csrf())
				.param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
		assertThat(confere("ate.demo", SENHA_ATE)).isTrue();
	}

	// --- CA-RF01-06: depois da troca ---

	@Test
	void trocaGravaHashNovoELimpaATemporaria() throws Exception {
		MockHttpSession sessao = entrar("nova.conta", TEMPORARIA);
		String idAntes = sessao.getId();

		mvc.perform(post(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao).with(csrf())
				.param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));

		Map<String, Object> linha = linha("nova.conta");
		assertThat((String) linha.get("senha_hash")).startsWith("{bcrypt}").doesNotContain(NOVA);
		assertThat(encoder.matches(NOVA, (String) linha.get("senha_hash"))).isTrue();
		assertThat(linha).containsEntry("troca_senha_obrigatoria", false);
		assertThat(linha.get("senha_temporaria_expira_em")).isNull();
		assertThat(sessao.getId()).isNotEqualTo(idAntes);
		// A troca acabou: as rotas voltam a funcionar na mesma sessão
		mvc.perform(get("/portaria").session(sessao)).andExpect(status().isOk());
	}

	@Test
	void depoisDaTrocaATemporariaNaoEntraEANovaVaiDiretoParaAPortaria() throws Exception {
		definirNovaSenha("nova.conta", TEMPORARIA, NOVA);

		mvc.perform(formLogin().user("nova.conta").password(TEMPORARIA)).andExpect(unauthenticated());
		mvc.perform(formLogin().user("nova.conta").password(NOVA))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	@Test
	void depoisDaTrocaAPortariaMostraOSucesso() throws Exception {
		HtmlPage definir = entrarPeloFormulario("nova.conta", TEMPORARIA);
		HtmlPage portaria = enviarDefinicao(definir, NOVA, NOVA);

		assertThat(portaria.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
		assertThat(portaria.querySelector("[role=status]").getTextContent()).contains("Sucesso:", "nova senha foi salva");
	}

	// --- Regras da nova senha (A1, passo 2; DEC-28, E4) ---

	@Test
	void novaSenhaCurtaEhRecusada() throws Exception {
		String curta = "a".repeat(tamanhoMinimo - 1);
		HtmlPage pagina = enviarDefinicao(entrarPeloFormulario("nova.conta", TEMPORARIA), curta, curta);

		assertThat(pagina.getElementById("nova-senha-erro").getTextContent())
				.contains("pelo menos " + tamanhoMinimo + " caracteres", "diferente da senha temporária");
		assertThat(confere("nova.conta", TEMPORARIA)).isTrue();
	}

	@Test
	void novaSenhaComOTamanhoMinimoEhAceita() throws Exception {
		String minima = "b".repeat(tamanhoMinimo);
		definirNovaSenha("nova.conta", TEMPORARIA, minima);

		assertThat(confere("nova.conta", minima)).isTrue();
	}

	@Test
	void novaSenhaIgualATemporariaEhRecusada() throws Exception {
		HtmlPage pagina = enviarDefinicao(entrarPeloFormulario("nova.conta", TEMPORARIA), TEMPORARIA, TEMPORARIA);

		assertThat(pagina.getElementById("nova-senha-erro").getTextContent()).contains("diferente da senha temporária");
		assertThat(linha("nova.conta")).containsEntry("troca_senha_obrigatoria", true);
	}

	@Test
	void confirmacaoDiferenteEhRecusada() throws Exception {
		HtmlPage pagina = enviarDefinicao(entrarPeloFormulario("nova.conta", TEMPORARIA), NOVA, NOVA + "x");

		assertThat(pagina.getElementById("confirmacao-erro").getTextContent())
				.contains("A confirmação não é igual à nova senha.");
		assertThat(confere("nova.conta", TEMPORARIA)).isTrue();
	}

	@Test
	void erroNaDefinicaoMarcaOCampoEFocaNele() throws Exception {
		// A11 §5.9.9: aria-invalid, aria-describedby e foco no primeiro campo com erro
		HtmlPage pagina = enviarDefinicao(entrarPeloFormulario("nova.conta", TEMPORARIA), "", "");

		assertThat(pagina.getTitleText()).isEqualTo("Definir nova senha | Furin Access");
		HtmlInput nova = (HtmlInput) pagina.getElementById("nova-senha");
		assertThat(nova.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(nova.getAttribute("aria-describedby")).isEqualTo("nova-senha-erro nova-senha-dica");
		assertThat(nova.hasAttribute("autofocus")).isTrue();
		assertThat(pagina.getElementById("nova-senha-erro").getAttribute("role")).isEqualTo("alert");
		assertThat(pagina.getElementById("nova-senha-erro").getTextContent()).contains("Informe a nova senha.");
		assertThat(pagina.getElementById("confirmacao").hasAttribute("autofocus")).isFalse();
	}

	@Test
	void telaDeDefinirTemAutocompleteDeNovaSenhaENaoDevolveAsSenhas() throws Exception {
		// CA-RF01-09 e CA-RF01-12
		HtmlPage pagina = enviarDefinicao(entrarPeloFormulario("nova.conta", TEMPORARIA), NOVA, NOVA + "x");

		for (String id : List.of("nova-senha", "confirmacao")) {
			HtmlInput campo = (HtmlInput) pagina.getElementById(id);
			assertThat(campo.getTypeAttribute()).isEqualTo("password");
			assertThat(campo.getAttribute("autocomplete")).isEqualTo("new-password");
			assertThat(campo.getValue()).isEmpty();
			assertThat(pagina.querySelectorAll("label[for='" + id + "']")).hasSize(1);
		}
		assertThat(pagina.getWebResponse().getContentAsString()).doesNotContain(NOVA).doesNotContain(TEMPORARIA);
		assertThat(pagina.querySelectorAll("[onpaste]")).isEmpty();
	}

	// --- CA-RF01-05: senha temporária vencida ---

	@Test
	void senhaTemporariaVencidaNaoAutenticaEOrientaAProcurarAEscola() throws Exception {
		conta("vencida.conta", TEMPORARIA, true, agora().minus(Duration.ofMinutes(1)));

		mvc.perform(formLogin().user("vencida.conta").password(TEMPORARIA))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());

		HtmlPage pagina = entrarPeloFormulario("vencida.conta", TEMPORARIA);
		assertThat(pagina.getUrl().getPath()).isEqualTo("/login");
		assertThat(pagina.getElementById("erro-senha-vencida").getTextContent()).contains(VENCIDA);
		assertThat(pagina.getElementById("erro-senha-vencida").getAttribute("role")).isEqualTo("alert");
	}

	@Test
	void venceExatamenteNoPrazoDaValidade() throws Exception {
		// Emitida agora, com o prazo de furin.senha.validade-temporaria (RN14)
		avancar(validadeTemporaria.minusSeconds(1));
		mvc.perform(formLogin().user("nova.conta").password(TEMPORARIA))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));

		avancar(Duration.ofSeconds(1));
		mvc.perform(formLogin().user("nova.conta").password(TEMPORARIA)).andExpect(unauthenticated());
	}

	@Test
	void senhaErradaNaContaVencidaMostraAMensagemGenerica() throws Exception {
		// A orientação só aparece para quem acertou a senha: não revela quais contas existem
		conta("vencida.conta", TEMPORARIA, true, agora().minus(Duration.ofMinutes(1)));

		HtmlPage pagina = entrarPeloFormulario("vencida.conta", ERRADA);

		assertThat(pagina.getElementById("erro-senha-vencida")).isNull();
		assertThat(pagina.getElementById("erro-login")).isNotNull();
	}

	@Test
	void senhaVencidaVaiParaOLogENaoContaParaOBloqueio(CapturedOutput saida) throws Exception {
		conta("vencida.conta", TEMPORARIA, true, agora().minus(Duration.ofMinutes(1)));

		for (int i = 0; i <= propriedades.login().maxTentativas(); i++) {
			mvc.perform(formLogin().user("vencida.conta").password(TEMPORARIA)).andExpect(unauthenticated());
		}

		assertThat(tentativas.bloqueado("vencida.conta")).isFalse();
		assertThat(saida.getOut()).contains("evento=LOGIN_FALHA", "motivo=SENHA_TEMPORARIA_VENCIDA");
		assertThat(saida.getAll()).doesNotContain(TEMPORARIA);
	}

	@Test
	void temporariaQueVenceDuranteASessaoNaoPodeSerTrocada() throws Exception {
		// A1, passo 2: a temporária ainda precisa estar no prazo na hora da troca
		avancar(validadeTemporaria.minusSeconds(1));
		MockHttpSession sessao = entrar("nova.conta", TEMPORARIA);
		avancar(Duration.ofSeconds(1));

		mvc.perform(post(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao).with(csrf())
				.param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());

		assertThat(confere("nova.conta", TEMPORARIA)).isTrue();
		assertThat(linha("nova.conta")).containsEntry("troca_senha_obrigatoria", true);
	}

	// --- CA-RF01-13: "Alterar senha" (fluxo A3) ---

	@Test
	void senhaAtualErradaNaoMudaNada(CapturedOutput saida) throws Exception {
		MockHttpSession sessao = entrar("ate.demo", SENHA_ATE);
		String hashAntes = (String) linha("ate.demo").get("senha_hash");

		mvc.perform(post("/senha/alterar").session(sessao).with(csrf())
				.param("senhaAtual", ERRADA).param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(status().isOk());

		assertThat(linha("ate.demo")).containsEntry("senha_hash", hashAntes);
		assertThat(saida.getOut()).contains("motivo=SENHA_ATUAL_INCORRETA").doesNotContain("evento=SENHA_ALTERADA");
	}

	@Test
	void senhaAtualCertaTrocaOHashEAAntigaDeixaDeAutenticar(CapturedOutput saida) throws Exception {
		MockHttpSession sessao = entrar("ate.demo", SENHA_ATE);
		String idAntes = sessao.getId();

		mvc.perform(post("/senha/alterar").session(sessao).with(csrf())
				.param("senhaAtual", SENHA_ATE).param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(redirectedUrl("/senha/alterar"));

		assertThat(confere("ate.demo", NOVA)).isTrue();
		assertThat(sessao.getId()).isNotEqualTo(idAntes);
		mvc.perform(formLogin().user("ate.demo").password(SENHA_ATE)).andExpect(unauthenticated());
		mvc.perform(formLogin().user("ate.demo").password(NOVA)).andExpect(authenticated());
		assertThat(saida.getOut()).contains("evento=SENHA_ALTERADA", "usuario=a***", "fluxo=ALTERACAO");
		assertThat(saida.getAll()).doesNotContain(NOVA).doesNotContain(SENHA_ATE);
	}

	@Test
	void alterarAplicaAsMesmasRegrasComASenhaAtual() throws Exception {
		HtmlPage alterar = paginaDeAlterar();
		HtmlPage igual = enviarAlteracao(alterar, SENHA_ATE, SENHA_ATE, SENHA_ATE);
		assertThat(igual.getElementById("nova-senha-erro").getTextContent()).contains("diferente da senha atual");

		String curta = "c".repeat(tamanhoMinimo - 1);
		HtmlPage pagina = enviarAlteracao(igual, SENHA_ATE, curta, curta);
		assertThat(pagina.getElementById("nova-senha-erro").getTextContent())
				.contains("pelo menos " + tamanhoMinimo + " caracteres");
		assertThat(confere("ate.demo", SENHA_ATE)).isTrue();
	}

	@Test
	void telaDeAlterarMostraOErroDaSenhaAtualEOSucesso() throws Exception {
		HtmlPage erro = enviarAlteracao(paginaDeAlterar(), ERRADA, NOVA, NOVA);

		assertThat(erro.getTitleText()).isEqualTo("Alterar senha | Furin Access");
		assertThat(erro.getElementById("senha-atual-erro").getTextContent()).contains("A senha atual não confere.");
		HtmlInput atual = (HtmlInput) erro.getElementById("senha-atual");
		assertThat(atual.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(atual.hasAttribute("autofocus")).isTrue();
		assertThat(atual.getValue()).isEmpty();
		assertThat(erro.getWebResponse().getContentAsString()).doesNotContain(ERRADA).doesNotContain(NOVA);

		HtmlPage sucesso = enviarAlteracao(erro, SENHA_ATE, NOVA, NOVA);
		assertThat(sucesso.getUrl().getPath()).isEqualTo("/senha/alterar");
		assertThat(sucesso.querySelector("[role=status]").getTextContent()).contains("Sucesso:", "senha foi alterada");
	}

	@Test
	void telaDeAlterarTemAutocompleteCertoEOMenuMarcado() throws Exception {
		// CA-RF01-09
		HtmlPage pagina = paginaDeAlterar();

		assertThat(pagina.getElementById("senha-atual").getAttribute("autocomplete")).isEqualTo("current-password");
		assertThat(pagina.getElementById("nova-senha").getAttribute("autocomplete")).isEqualTo("new-password");
		assertThat(pagina.getElementById("confirmacao").getAttribute("autocomplete")).isEqualTo("new-password");
		assertThat(pagina.querySelector("nav a[aria-current='page']").getTextContent().trim())
				.isEqualTo("Alterar senha");
	}

	@Test
	void senhaAtualErradaContaParaOBloqueioDoLogin() throws Exception {
		// RF01, exceções: conta como tentativa sem sucesso do RF02 [PROP]
		MockHttpSession sessao = entrar("ate.demo", SENHA_ATE);
		for (int i = 0; i < propriedades.login().maxTentativas(); i++) {
			mvc.perform(post("/senha/alterar").session(sessao).with(csrf())
					.param("senhaAtual", ERRADA).param("novaSenha", NOVA).param("confirmacao", NOVA));
		}
		assertThat(tentativas.bloqueado("ate.demo")).isTrue();

		// Bloqueado: nem a senha atual certa troca a senha
		mvc.perform(post("/senha/alterar").session(sessao).with(csrf())
				.param("senhaAtual", SENHA_ATE).param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(status().isOk());
		assertThat(confere("ate.demo", SENHA_ATE)).isTrue();
	}

	@Test
	void definicaoVaiParaOLogSemASenha(CapturedOutput saida) throws Exception {
		definirNovaSenha("nova.conta", TEMPORARIA, NOVA);

		assertThat(saida.getOut()).contains("evento=SENHA_ALTERADA", "usuario=n***", "fluxo=DEFINICAO");
		assertThat(saida.getAll()).doesNotContain(NOVA).doesNotContain(TEMPORARIA);
	}

	// --- Apoio ---

	private MockHttpSession entrar(String login, String senha) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin().user(login).password(senha))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	private void definirNovaSenha(String login, String temporaria, String nova) throws Exception {
		mvc.perform(post(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(entrar(login, temporaria)).with(csrf())
				.param("novaSenha", nova).param("confirmacao", nova))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	private HtmlPage entrarPeloFormulario(String login, String senha) throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).setValue(login);
		((HtmlInput) pagina.getElementById("senha")).setValue(senha);
		return ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
	}

	private static HtmlPage enviarDefinicao(HtmlPage definir, String nova, String confirmacao) throws Exception {
		((HtmlInput) definir.getElementById("nova-senha")).setValue(nova);
		((HtmlInput) definir.getElementById("confirmacao")).setValue(confirmacao);
		return ((HtmlButton) definir.querySelector("main form button[type=submit]")).click();
	}

	private HtmlPage paginaDeAlterar() throws Exception {
		entrarPeloFormulario("ate.demo", SENHA_ATE);
		return navegador.getPage("http://localhost/senha/alterar");
	}

	private static HtmlPage enviarAlteracao(HtmlPage alterar, String atual, String nova, String confirmacao)
			throws Exception {
		((HtmlInput) alterar.getElementById("senha-atual")).setValue(atual);
		return enviarDefinicao(alterar, nova, confirmacao);
	}

	private boolean confere(String login, String senha) {
		return encoder.matches(senha, (String) linha(login).get("senha_hash"));
	}

	private Map<String, Object> linha(String login) {
		return jdbc.queryForMap("SELECT senha_hash, troca_senha_obrigatoria, senha_temporaria_expira_em "
				+ "FROM usuario WHERE login = ?", login);
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

	/** Agora, pelo relógio do teste, em UTC (RN10). */
	private LocalDateTime agora() {
		return LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
	}

	private void conta(String login, String senha, boolean trocaObrigatoria, LocalDateTime expiraEm) {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, ?, ?, ?)",
				"Conta Teste", login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, trocaObrigatoria,
				expiraEm, agora());
	}

}
