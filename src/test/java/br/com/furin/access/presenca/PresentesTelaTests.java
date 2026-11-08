package br.com.furin.access.presenca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomNode;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.TipoMovimentacao;

/**
 * Atualização da tela Presentes (T-34; RF16, passos 4 e 5; CA-RF16-09), lida como num
 * navegador (HtmlUnit), com o JavaScript ligado. O intervalo fica em 1 s para o teste não
 * esperar 30 s.
 * <p>
 * Sem a transação do teste: os pedidos do JavaScript correm em outra thread, com outra
 * conexão. A cena é gravada antes de cada teste e apagada depois.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "furin.presentes.intervalo-atualizacao=1s")
class PresentesTelaTests {

	private static final String FUNCIONARIO = "presentes.tela";
	private static final String SENHA = "descartavel-presentes-T34";
	private static final String MATRICULA = "T34T-001";
	private static final String TURMA = "6A-T34T";

	/** 20/10/2026, 12:30 no horário da escola. */
	private static final Instant AGORA = LocalDateTime.of(2026, 10, 20, 12, 30)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(AGORA);
	}

	@Autowired
	private WebClient navegador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	private CenaDaPortaria cena;
	private long idFuncionario;
	private long vinculo;
	private long carteirinha;

	@BeforeEach
	void preparar() {
		apagarACena();
		cena = new CenaDaPortaria(jdbc);
		Instant antes = AGORA.minus(Duration.ofDays(30));
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', ?, ?, ?, 1, 0, NULL, ?)",
				FUNCIONARIO, encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO, CenaDaPortaria.utc(antes));
		idFuncionario = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Long.class, FUNCIONARIO);
		long turma = cena.turma(TURMA);
		long ana = cena.aluno(MATRICULA, "Ana Teste", true, antes);
		vinculo = cena.vinculo(ana, turma, antes, null);
		carteirinha = cena.carteirinha(ana, UUID.randomUUID().toString().replace("-", ""), antes, null, null);
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(true);
		navegador.getOptions().setThrowExceptionOnScriptError(true);
		navegador.getCookieManager().clearCookies();
	}

	@AfterEach
	void pararOsTemporizadoresEApagarACena() {
		navegador.getCurrentWindow().getJobManager().removeAllJobs();
		navegador.getOptions().setJavaScriptEnabled(false);
		apagarACena();
	}

	/** Também no começo: uma execução interrompida não deixa a cena pela metade. */
	private void apagarACena() {
		jdbc.update("DELETE r FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "JOIN aluno a ON a.id_aluno = v.id_aluno WHERE a.matricula = ?", MATRICULA);
		jdbc.update("DELETE c FROM carteirinha c JOIN aluno a ON a.id_aluno = c.id_aluno WHERE a.matricula = ?",
				MATRICULA);
		jdbc.update("DELETE v FROM vinculo_turma v JOIN aluno a ON a.id_aluno = v.id_aluno WHERE a.matricula = ?",
				MATRICULA);
		jdbc.update("DELETE FROM aluno WHERE matricula = ?", MATRICULA);
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	@Test
	void atualizacaoAutomaticaTrocaOsNumerosSemAnunciar() throws Exception {
		HtmlPage pagina = abrir();
		int antes = total(pagina);
		assertThat(linhaDaTurma(pagina)).isEqualTo(TURMA + " Manhã 0");

		entradaDaAnaHoje();
		esperar(Duration.ofMillis(2500));

		assertThat(total(pagina)).isEqualTo(antes + 1);
		assertThat(linhaDaTurma(pagina)).isEqualTo(TURMA + " Manhã 1");
		// CA-RF16-09; A11 p.9: a atualização automática não escreve na região viva
		assertThat(texto(pagina, "anuncio-presentes")).isEmpty();
		assertThat(pagina.getElementById("falha-atualizacao").hasAttribute("hidden")).isTrue();
	}

	@Test
	void atualizarAnunciaOTotalSemMoverOFoco() throws Exception {
		HtmlPage pagina = abrir();
		int antes = total(pagina);
		entradaDaAnaHoje();
		HtmlButton atualizar = (HtmlButton) pagina.getElementById("botao-atualizar");

		atualizar.click();
		esperar(Duration.ofMillis(600));

		assertThat(total(pagina)).isEqualTo(antes + 1);
		// DEC-28, C3: só o "Atualizar" é anunciado, e o foco fica no botão
		assertThat(texto(pagina, "anuncio-presentes")).isEqualTo("Atualizado às 12:30. Presentes agora: " + (antes + 1) + ".");
		assertThat(pagina.getFocusedElement()).isEqualTo(atualizar);
	}

	@Test
	void pendenciaFechadaSaiDaListaNaAtualizacao() throws Exception {
		// CA-RF16-04, na tela: a saída de outro funcionário fecha a pendência
		cena.confirmada(vinculo, idFuncionario, carteirinha, TipoMovimentacao.ENTRADA, AGORA.minus(Duration.ofDays(4)));
		HtmlPage pagina = abrir();
		assertThat(pagina.querySelectorAll("#dados-presentes a[href^='/portaria/manual/']")).hasSize(1);

		cena.confirmada(vinculo, idFuncionario, carteirinha, TipoMovimentacao.SAIDA,
				AGORA.minus(Duration.ofDays(4)).plus(Duration.ofHours(5)));
		((HtmlButton) pagina.getElementById("botao-atualizar")).click();
		esperar(Duration.ofMillis(600));

		assertThat(pagina.querySelectorAll("#dados-presentes a[href^='/portaria/manual/']")).isEmpty();
	}

	// --- Apoio ---

	private void entradaDaAnaHoje() {
		cena.confirmada(vinculo, idFuncionario, carteirinha, TipoMovimentacao.ENTRADA, AGORA.minus(Duration.ofHours(5)));
	}

	/** Entra pelo formulário de login e abre a tela Presentes pelo menu. */
	private HtmlPage abrir() throws Exception {
		HtmlPage login = navegador.getPage("http://localhost/login");
		((HtmlInput) login.getElementById("usuario")).setValue(FUNCIONARIO);
		((HtmlInput) login.getElementById("senha")).setValue(SENHA);
		HtmlPage portaria = ((HtmlButton) login.querySelector("main form button[type=submit]")).click();
		HtmlPage presentes = portaria.getAnchorByText("Presentes").click();
		assertThat(presentes.getUrl().getPath()).isEqualTo("/presentes");
		return presentes;
	}

	private String linhaDaTurma(HtmlPage pagina) {
		return pagina.querySelectorAll("#tabela-turmas tbody tr").stream().map(PresentesTelaTests::texto)
				.filter(linha -> linha.startsWith(TURMA + " ")).findFirst().orElse("");
	}

	private static int total(HtmlPage pagina) {
		return Integer.parseInt(texto(pagina, "total-presentes"));
	}

	private void esperar(Duration tempo) {
		navegador.waitForBackgroundJavaScript(tempo.toMillis());
	}

	private static String texto(HtmlPage pagina, String id) {
		return texto(pagina.getElementById(id));
	}

	private static String texto(DomNode no) {
		return no.getTextContent().replaceAll("\\s+", " ").trim();
	}

}
