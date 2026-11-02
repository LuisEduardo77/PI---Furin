package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.SegurancaConfig;

/**
 * O aviso de expiração funcionando no navegador, com o JavaScript ligado (T-16; RF01,
 * fluxo A2; CA-RF01-10; DEC-40). Os tempos são trocados por poucos segundos só neste
 * teste: a validação da T-07 não aceita menos de 1 minuto em {@code furin.sessao.*}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AvisoDeSessaoTests {

	private static final String SENHA = "descartavel-ate-T16";

	private static final Duration INATIVIDADE = Duration.ofSeconds(6);
	private static final Duration AVISO_ANTES = Duration.ofSeconds(4);

	/** Aviso 2 s depois de a página carregar; fim da sessão (para a página) 4 s depois. */
	@TestBean
	private TemposDaSessao tempos;

	static TemposDaSessao tempos() {
		return new TemposDaSessao(INATIVIDADE, AVISO_ANTES);
	}

	@Autowired
	private WebClient navegador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private TentativasDeLogin tentativas;

	@BeforeEach
	void preparar() {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, 0, NULL, ?)",
				"ATE Demo", "ate.demo", encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO,
				LocalDateTime.now(ZoneOffset.UTC));
		tentativas.esquecerTodos();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(true);
		navegador.getOptions().setThrowExceptionOnScriptError(true);
		navegador.getCookieManager().clearCookies();
	}

	@AfterEach
	void pararOsTemporizadores() {
		navegador.getCurrentWindow().getJobManager().removeAllJobs();
		navegador.getOptions().setJavaScriptEnabled(false);
	}

	@Test
	void antesDoAvisoODialogoFicaFechado() throws Exception {
		HtmlPage portaria = entrar();

		assertThat(portaria.getElementById("aviso-sessao").hasAttribute("open")).isFalse();
	}

	@Test
	void avisoAbreComOFocoNoBotaoEOTempoRestante() throws Exception {
		HtmlPage portaria = entrar();
		esperar(INATIVIDADE.minus(AVISO_ANTES).plusMillis(500));

		DomElement aviso = portaria.getElementById("aviso-sessao");
		assertThat(aviso.hasAttribute("open")).isTrue();
		// DEC-40: o foco vai para "Continuar conectado"
		assertThat(portaria.getFocusedElement().getTextContent()).isEqualTo("Continuar conectado");
		assertThat(aviso.querySelector("[data-tempo-restante]").getTextContent()).matches("[1-4] segundos?");
	}

	@Test
	void continuarConectadoFechaOAvisoERecomecaAContagem() throws Exception {
		HtmlPage portaria = entrar();
		esperar(INATIVIDADE.minus(AVISO_ANTES).plusMillis(500));

		((HtmlButton) portaria.getElementById("aviso-sessao").querySelector("[data-continuar]")).click();
		esperar(Duration.ofMillis(500));

		HtmlPage atual = (HtmlPage) navegador.getCurrentWindow().getEnclosedPage();
		assertThat(atual.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
		assertThat(atual.getElementById("aviso-sessao").hasAttribute("open")).isFalse();

		// A contagem recomeçou: o aviso volta depois do mesmo intervalo
		esperar(INATIVIDADE.minus(AVISO_ANTES).plusMillis(500));
		assertThat(atual.getElementById("aviso-sessao").hasAttribute("open")).isTrue();
	}

	private HtmlPage entrar() throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).setValue("ate.demo");
		((HtmlInput) pagina.getElementById("senha")).setValue(SENHA);
		HtmlPage portaria = ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
		assertThat(portaria.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
		return portaria;
	}

	/** Deixa o JavaScript da página rodar durante esse tempo (temporizadores e pedidos). */
	private void esperar(Duration tempo) {
		navegador.waitForBackgroundJavaScript(tempo.toMillis());
	}

}
