package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

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
 * Aceite obrigatório do Termo de uso e da Política de privacidade (DEC-47): no primeiro
 * acesso, toda rota leva à tela do aceite; o aceite grava a versão atual na conta e registra
 * {@code TERMOS_ACEITOS}; uma versão nova pede o aceite de novo; com a senha temporária, a
 * troca vem antes do aceite. A tela de login tem os dois botões e as duas janelas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class AceiteDosTermosTests {

	private static final String SENHA = "descartavel-termos";
	private static final String TEMPORARIA = "temporaria-termos";
	private static final String NOVA = "nova-senha-termos";
	private static final String ACEITE = AceiteDosTermosObrigatorio.TELA_DO_ACEITE;

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(Instant.parse("2026-11-17T12:00:00Z"));
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private TentativasDeLogin tentativas;

	@Autowired
	private FurinProperties propriedades;

	private String versaoAtual;

	@BeforeEach
	void preparar() {
		versaoAtual = propriedades.termos().versaoAtual();
		tentativas.esquecerTodos();
		conta("termos.novo", SENHA, false, null, null);
		conta("termos.aceitos", SENHA, false, null, versaoAtual);
		conta("termos.antigos", SENHA, false, null, "v0-versao-anterior");
		conta("termos.temporaria", TEMPORARIA, true, agora().plusHours(1), null);
	}

	// --- Primeiro acesso ---

	@Test
	void primeiroLoginLevaAoAceite() throws Exception {
		mvc.perform(formLogin().user("termos.novo").password(SENHA))
				.andExpect(authenticated())
				.andExpect(redirectedUrl(ACEITE));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/portaria", "/alunos", "/presentes", "/historico", "/contas/equipe", "/senha/alterar" })
	void semOAceiteTodaRotaLevaAoAceite(String rota) throws Exception {
		mvc.perform(get(rota).session(entrar("termos.novo")))
				.andExpect(redirectedUrl(ACEITE));
	}

	@Test
	void telaDoAceiteMostraOsDoisTextosComAVersao() throws Exception {
		String pagina = mvc.perform(get(ACEITE).session(entrar("termos.novo")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(pagina).contains("<title>Termo de uso e Política de privacidade | Furin Access</title>",
				"id=\"titulo-termo-de-uso\"", "id=\"titulo-politica-de-privacidade\"",
				"Versão " + versaoAtual, "Li e aceito");
	}

	@Test
	void sairContinuaLiberadoSemOAceite() throws Exception {
		mvc.perform(post("/logout").session(entrar("termos.novo")).with(csrf()))
				.andExpect(redirectedUrl("/login?logout"));
	}

	// --- Aceite ---

	@Test
	void aceiteGravaAVersaoEOInstanteELevaAPortaria() throws Exception {
		MockHttpSession sessao = entrar("termos.novo");

		mvc.perform(post(ACEITE).session(sessao).with(csrf()))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));

		Map<String, Object> linha = jdbc.queryForMap(
				"SELECT termos_versao_aceita, termos_aceitos_em FROM usuario WHERE login = 'termos.novo'");
		assertThat(linha.get("termos_versao_aceita")).isEqualTo(versaoAtual);
		assertThat(linha.get("termos_aceitos_em")).isEqualTo(agora());
		// Depois do aceite, a portaria abre sem voltar ao aceite
		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao)).andExpect(status().isOk());
	}

	@Test
	void aceiteVaiParaOLogSoComAVersao(CapturedOutput saida) throws Exception {
		mvc.perform(post(ACEITE).session(entrar("termos.novo")).with(csrf()));

		assertThat(saida.getOut()).contains("evento=TERMOS_ACEITOS", "usuario=t***", "versao=" + versaoAtual);
	}

	@Test
	void quemJaAceitouAVersaoAtualVaiDiretoAPortaria() throws Exception {
		mvc.perform(formLogin().user("termos.aceitos").password(SENHA))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
		mvc.perform(get(ACEITE).session(entrar("termos.aceitos")))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
	}

	@Test
	void versaoNovaPedeOAceiteDeNovo() throws Exception {
		mvc.perform(formLogin().user("termos.antigos").password(SENHA))
				.andExpect(redirectedUrl(ACEITE));
	}

	@Test
	void aceiteSemPendenciaNaoGravaNada() throws Exception {
		mvc.perform(post(ACEITE).session(entrar("termos.aceitos")).with(csrf()))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));

		assertThat(jdbc.queryForObject("SELECT termos_aceitos_em FROM usuario WHERE login = 'termos.aceitos'",
				LocalDateTime.class)).isEqualTo(agora().minusDays(1));
	}

	@Test
	void aceiteSemCsrfERecusado() throws Exception {
		mvc.perform(post(ACEITE).session(entrar("termos.novo")))
				.andExpect(status().isForbidden());
	}

	// --- Junto com a senha temporária ---

	@Test
	void trocaDeSenhaVemAntesDoAceite() throws Exception {
		MockHttpSession sessao = (MockHttpSession) mvc.perform(formLogin().user("termos.temporaria").password(TEMPORARIA))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA))
				.andReturn().getRequest().getSession(false);
		// Com as duas pendentes, a tela do aceite também leva à troca
		mvc.perform(get(ACEITE).session(sessao)).andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));

		mvc.perform(post(TrocaDeSenhaObrigatoria.TELA_DE_TROCA).session(sessao).with(csrf())
				.param("novaSenha", NOVA).param("confirmacao", NOVA))
				.andExpect(redirectedUrl(SegurancaConfig.TELA_INICIAL));
		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao)).andExpect(redirectedUrl(ACEITE));
	}

	// --- Tela de login ---

	@Test
	void loginTemOsDoisBotoesEAsDuasJanelas() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(content().string(allOf(
						containsString("data-abrir-janela=\"janela-termo-de-uso\""),
						containsString("data-abrir-janela=\"janela-politica-de-privacidade\""),
						containsString("<dialog id=\"janela-termo-de-uso\""),
						containsString("<dialog id=\"janela-politica-de-privacidade\""),
						containsString(">Termo de uso</button>"),
						containsString(">Política de privacidade</button>"),
						containsString("/js/termos.js"))));
	}

	// --- Apoio ---

	private MockHttpSession entrar(String login) throws Exception {
		return (MockHttpSession) mvc.perform(formLogin().user(login).password(SENHA))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	/** Agora, pelo relógio do teste, em UTC (RN10). */
	private LocalDateTime agora() {
		return LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
	}

	private void conta(String login, String senha, boolean trocaObrigatoria, LocalDateTime expiraEm,
			String versaoAceita) {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, termos_versao_aceita, termos_aceitos_em, criado_em) "
				+ "VALUES (?, ?, ?, ?, 1, ?, ?, ?, ?, ?)",
				"Conta Teste", login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, trocaObrigatoria,
				expiraEm, versaoAceita, versaoAceita == null ? null : agora().minusDays(1), agora());
	}

}
