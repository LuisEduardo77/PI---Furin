package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Tela da portaria no perfil {@code prototipo} (T-30; RF09, RF10, RF14, RF22; RNF06;
 * DEC-25), lida como num navegador (HtmlUnit), com o JavaScript ligado. A carga V2 fica
 * desligada ({@code furin.carga.ativa=false}).
 * <p>
 * Sem a transação do teste: os pedidos do JavaScript correm em outra thread, com outra
 * conexão, e só enxergam dados gravados de verdade. A cena é gravada antes de cada teste e
 * apagada depois.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
class PortariaTelaTests {

	private static final String FUNCIONARIO = "portaria.tela";
	private static final String SENHA = "descartavel-portaria-T30";

	private static final String MATRICULA = "T30-001";
	private static final String TURMA = "6A-T30";

	private static final String CODIGO_ANA = "c1b2c3d4e5f60718293a4b5c6d7e8f92";
	private static final String CODIGO_REVOGADO = "d1b2c3d4e5f60718293a4b5c6d7e8f93";
	private static final String MATRICULA_BIA = "T30-002";
	private static final String CODIGO_BIA = "e1b2c3d4e5f60718293a4b5c6d7e8f94";
	private static final String ESPERA = "Aproxime sua carteirinha do leitor.";
	private static final String MONITOR_RECUSA = "Não foi possível ler sua carteirinha. Tente de novo ou procure "
			+ "o funcionário da portaria.";

	/** 20/10/2026, 07:02 no horário da escola. */
	private static final Instant AGORA = LocalDateTime.of(2026, 10, 20, 7, 2)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	/** Nunca a área privada de verdade: a foto deste teste é só uma imagem de 1 pixel. */
	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-portaria-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", AREA_PRIVADA::toString);
	}

	@AfterAll
	static void apagarAAreaPrivada() throws IOException {
		FileSystemUtils.deleteRecursively(AREA_PRIVADA);
	}

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
	private FurinProperties propriedades;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private AreaPrivada areaPrivada;

	@BeforeEach
	void preparar() {
		apagarACena();
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant ontem = AGORA.minus(Duration.ofDays(1));
		// Login de verdade pelo formulário: os pedidos do JavaScript correm em outra thread,
		// onde o usuário de teste do Spring Security não existe
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', ?, ?, ?, 1, 0, NULL, ?)",
				FUNCIONARIO, encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO, CenaDaPortaria.utc(ontem));
		long turma = cena.turma(TURMA);
		long ana = cena.aluno(MATRICULA, "Ana Teste", true, ontem);
		cena.vinculo(ana, turma, ontem.minus(Duration.ofDays(30)), null);
		cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
		// Uma carteirinha revogada da mesma aluna (segunda via): não aparece no painel
		cena.carteirinha(ana, CODIGO_REVOGADO, ontem.minus(Duration.ofDays(10)), null, ontem);
		// Outra aluna, para a RN05
		long bia = cena.aluno(MATRICULA_BIA, "Bia Teste", true, ontem);
		cena.vinculo(bia, turma, ontem.minus(Duration.ofDays(30)), null);
		cena.carteirinha(bia, CODIGO_BIA, ontem, null, null);
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
				+ "JOIN aluno a ON a.id_aluno = v.id_aluno WHERE a.matricula IN (?, ?)", MATRICULA, MATRICULA_BIA);
		jdbc.update("DELETE c FROM carteirinha c JOIN aluno a ON a.id_aluno = c.id_aluno WHERE a.matricula IN (?, ?)",
				MATRICULA, MATRICULA_BIA);
		jdbc.update("DELETE v FROM vinculo_turma v JOIN aluno a ON a.id_aluno = v.id_aluno "
				+ "WHERE a.matricula IN (?, ?)", MATRICULA, MATRICULA_BIA);
		jdbc.update("DELETE FROM aluno WHERE matricula IN (?, ?)", MATRICULA, MATRICULA_BIA);
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	// --- Estrutura da tela ---

	@Test
	void telaTemCampoDeLeituraComFocoEPaineisSeparados() throws Exception {
		HtmlPage portaria = abrir();

		assertThat(portaria.getTitleText()).isEqualTo("Portaria | Furin Access");
		HtmlInput campo = (HtmlInput) portaria.getElementById("campo-leitura");
		assertThat(portaria.querySelector("label[for='campo-leitura']").getTextContent())
				.isEqualTo("Código da carteirinha");
		assertThat(portaria.getFocusedElement()).isEqualTo(campo);

		// CA-RF22-06: duas regiões role="status", separadas
		DomElement painel = portaria.getElementById("painel-funcionario");
		DomElement monitor = portaria.getElementById("monitor-aluno");
		assertThat(painel.getAttribute("role")).isEqualTo("status");
		assertThat(monitor.getAttribute("role")).isEqualTo("status");
		assertThat(painel.isAncestorOf(monitor) || monitor.isAncestorOf(painel)).isFalse();
		// CA-RF22-05: em espera, nenhum dado pessoal no monitor
		assertThat(monitor.getTextContent()).isEqualTo(ESPERA);
		assertThat(portaria.querySelectorAll("script[src='/js/portaria.js']")).hasSize(1);
		// CA-RF13-08: nenhum seletor nem texto de "modo" de entrada ou saída
		assertThat(portaria.querySelector("main").getTextContent()).doesNotContain("Modo");
		assertThat(portaria.querySelectorAll("main select, main input[type=radio]")).isEmpty();
	}

	@Test
	void simulacoesAparecemIdentificadasComUmBotaoPorCarteirinhaValida() throws Exception {
		// DEC-25; CA-RF09-11: as simulações são identificadas
		HtmlPage portaria = abrir();

		assertThat(portaria.getElementById("titulo-simulacao").getTextContent()).isEqualTo("Simular leitura (protótipo)");
		List<String> botoes = portaria.querySelectorAll("[data-simular-leitura]").stream()
				.map(botao -> botao.getTextContent().trim()).toList();
		assertThat(botoes).contains("Ler carteirinha de Ana Teste");
		// A carteirinha revogada não entra no painel
		assertThat(portaria.querySelectorAll("[data-codigo='" + CODIGO_REVOGADO + "']")).isEmpty();

		HtmlButton giro = (HtmlButton) portaria.getElementById("botao-giro");
		assertThat(giro.getTextContent()).isEqualTo("Simular passagem (giro)");
		assertThat(giro.isDisabled()).as("sem liberação, não há o que girar").isTrue();
	}

	// --- Leitura e giro (RF09, RF10, RF22) ---

	@Test
	void simularLeituraLiberaAEntradaNosDoisPaineis() throws Exception {
		HtmlPage portaria = abrir();

		simularLeituraDaAna(portaria);

		// CA-RF10-01 e 03: nome, turma, tipo e situação em texto
		String painel = texto(portaria, "painel-funcionario");
		assertThat(painel).contains("Ana Teste", "6A-T30 — Manhã", "Entrada",
				"Entrada liberada — aguardando passagem");
		// CA-RF22-01 e 04: só o primeiro nome e o estado
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, entrada liberada. Pode passar.");
		assertThat(texto(portaria, "monitor-aluno")).doesNotContain("Teste", "6A");
		// O foco volta ao campo de leitura (A11 §5.1.13), e o giro fica disponível
		assertThat(portaria.getFocusedElement().getId()).isEqualTo("campo-leitura");
		assertThat(((HtmlButton) portaria.getElementById("botao-giro")).isDisabled()).isFalse();
	}

	// --- Foto (T-18; RF10; DEC-28, A9) ---

	@Test
	void fotoQueExisteApareceComTextoAlternativoSoNoPainelDoFuncionario() throws Exception {
		String chave = areaPrivada.guardar(TipoDeDocumento.FOTO, new ByteArrayInputStream(imagemPng()));
		jdbc.update("UPDATE aluno SET foto_chave = ? WHERE matricula = ?", chave, MATRICULA);
		HtmlPage portaria = abrir();

		simularLeituraDaAna(portaria);

		// CA-RF10-02: a foto tem o nome no texto alternativo, e o nome também aparece em texto
		DomElement foto = portaria.querySelector("#painel-funcionario img");
		assertThat(foto).as("a foto está no painel").isNotNull();
		assertThat(foto.getAttribute("src")).isEqualTo("/arquivos/" + chave);
		assertThat(foto.getAttribute("alt")).isEqualTo("Foto de identificação de Ana Teste");
		assertThat(texto(portaria, "painel-funcionario")).contains("Ana Teste").doesNotContain("Foto indisponível");
		// CA-RF10-04: nada de foto no monitor do aluno
		assertThat(portaria.querySelectorAll("#monitor-aluno img")).isEmpty();
	}

	@Test
	void fotoContinuaNoPainelDepoisDoGiro() throws Exception {
		String chave = areaPrivada.guardar(TipoDeDocumento.FOTO, new ByteArrayInputStream(imagemPng()));
		jdbc.update("UPDATE aluno SET foto_chave = ? WHERE matricula = ?", chave, MATRICULA);
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);

		((HtmlButton) portaria.getElementById("botao-giro")).click();
		esperar(Duration.ofMillis(800));

		DomElement foto = portaria.querySelector("#painel-funcionario img");
		assertThat(foto.getAttribute("src")).isEqualTo("/arquivos/" + chave);
	}

	@Test
	void fotoQueNaoCarregaMostraOAvisoESemBloquearALiberacao() throws Exception {
		// A cena grava uma chave sem arquivo. DEC-28, A9: o giro continua disponível
		HtmlPage portaria = abrir();

		simularLeituraDaAna(portaria);

		assertThat(portaria.querySelectorAll("#painel-funcionario img")).isEmpty();
		assertThat(texto(portaria, "painel-funcionario")).contains("Foto indisponível", "Ana Teste");
		confirmarOGiro(portaria);
	}

	// --- Giro ---

	@Test
	void simularPassagemRegistraAEntradaEOMonitorVoltaAEspera() throws Exception {
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);

		((HtmlButton) portaria.getElementById("botao-giro")).click();
		esperar(Duration.ofMillis(800));

		assertThat(texto(portaria, "painel-funcionario")).contains("Entrada registrada às 07:02");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, entrada registrada.");
		assertThat(portaria.getFocusedElement().getId()).isEqualTo("campo-leitura");
		assertThat(((HtmlButton) portaria.getElementById("botao-giro")).isDisabled()).isTrue();
		assertThat(jdbc.queryForObject("SELECT situacao FROM registro_acesso r JOIN carteirinha c "
				+ "ON c.id_carteirinha = r.id_carteirinha WHERE c.codigo = ?", String.class, CODIGO_ANA))
				.isEqualTo("CONFIRMADA");

		// CA-RF22-02: depois de furin.portaria.monitor-exibicao, o monitor volta à espera
		esperar(propriedades.portaria().monitorExibicao().plusMillis(500));
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo(ESPERA);
	}

	@Test
	void codigoDigitadoComEnterTambemRegistraALeitura() throws Exception {
		// RNF07: o leitor USB "digita" o código e tecla Enter
		HtmlPage portaria = abrir();
		HtmlInput campo = (HtmlInput) portaria.getElementById("campo-leitura");

		campo.type(CODIGO_ANA);
		campo.type('\n');
		esperar(Duration.ofMillis(800));

		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, entrada liberada. Pode passar.");
		assertThat(campo.getValue()).as("o campo fica vazio para a próxima leitura (CA-RF09-09)").isEmpty();
		assertThat(portaria.getFocusedElement()).isEqualTo(campo);
	}

	@Test
	void leituraRecusadaMostraOMotivoSoAoFuncionario() throws Exception {
		HtmlPage portaria = abrir();
		HtmlInput campo = (HtmlInput) portaria.getElementById("campo-leitura");

		campo.type(CODIGO_REVOGADO);
		campo.type('\n');
		esperar(Duration.ofMillis(800));

		// Motivo técnico até a T-31; o monitor recebe só a mensagem genérica (RF22; RN17)
		assertThat(texto(portaria, "painel-funcionario")).contains("Leitura recusada", "CARTEIRINHA_REVOGADA");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo(MONITOR_RECUSA)
				.doesNotContain("Ana", "REVOGADA");
		assertThat(((HtmlButton) portaria.getElementById("botao-giro")).isDisabled()).isTrue();
		assertThat(portaria.getFocusedElement()).isEqualTo(campo);
	}

	// --- Aviso do leitor (DEC-28, A8; CA-RNF07-04) ---

	@Test
	void focoForaDoCampoAvisaQueOLeitorNaoEstaProntoEClicarNaTelaODevolve() throws Exception {
		HtmlPage portaria = abrir();
		assertThat(texto(portaria, "aviso-leitor")).isEmpty();
		assertThat(portaria.getElementById("aviso-leitor").getAttribute("role")).isEqualTo("status");

		((HtmlButton) portaria.querySelector("[data-codigo='" + CODIGO_ANA + "']")).focus();
		esperar(Duration.ofMillis(500));
		assertThat(texto(portaria, "aviso-leitor")).isEqualTo("Leitor não está pronto. Clique no campo de leitura.");

		portaria.getElementById("titulo-leitura").click();
		esperar(Duration.ofMillis(300));
		assertThat(portaria.getFocusedElement().getId()).isEqualTo("campo-leitura");
		assertThat(texto(portaria, "aviso-leitor")).isEmpty();
	}

	// --- Leitura repetida (RN03, RN04): a liberação pendente nunca fica sem saída pela tela ---

	@Test
	void leituraRepetidaMantemOGiroDisponivel() throws Exception {
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);

		// RN03: o relógio está parado, então a segunda leitura cai na janela de 2 s
		simularLeituraDaAna(portaria);
		assertThat(texto(portaria, "painel-funcionario")).contains("Ana Teste", "Leitura já recebida");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, pode passar.");

		// RN04: passada a janela, sem giro, continua a mesma liberação
		((RelogioAjustavel) relogio).avancar(Duration.ofSeconds(5));
		simularLeituraDaAna(portaria);
		assertThat(texto(portaria, "painel-funcionario")).contains("Ana Teste", "Aguardando passagem");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, pode passar.");
		confirmarOGiro(portaria);
	}

	@Test
	void comAnaAguardandoALeituraDeOutroAlunoPedeParaAguardar() throws Exception {
		// RN05: o funcionário vê o motivo; o aluno vê só "Aguarde" (RN09)
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);
		HtmlInput campo = (HtmlInput) portaria.getElementById("campo-leitura");

		campo.type(CODIGO_BIA);
		campo.type('\n');
		esperar(Duration.ofMillis(800));

		assertThat(texto(portaria, "painel-funcionario")).isEqualTo("Aguarde a passagem do aluno anterior");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Aguarde.");
		assertThat(portaria.getFocusedElement()).isEqualTo(campo);
		confirmarOGiro(portaria);
	}

	@Test
	void recusaDeOutraLeituraNaoPerdeALiberacaoEmCurso() throws Exception {
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);
		HtmlInput campo = (HtmlInput) portaria.getElementById("campo-leitura");

		campo.type("abc");
		campo.type('\n');
		esperar(Duration.ofMillis(800));

		assertThat(texto(portaria, "painel-funcionario")).contains("CODIGO_NAO_RECONHECIDO");
		confirmarOGiro(portaria);
	}

	@Test
	void depoisDeRecarregarAPaginaNovaLeituraRecuperaALiberacao() throws Exception {
		HtmlPage portaria = abrir();
		simularLeituraDaAna(portaria);

		HtmlPage recarregada = navegador.getPage("http://localhost/portaria");
		assertThat(((HtmlButton) recarregada.getElementById("botao-giro")).isDisabled()).isTrue();

		simularLeituraDaAna(recarregada);
		confirmarOGiro(recarregada);
	}

	// --- Apoio ---

	private void confirmarOGiro(HtmlPage portaria) throws Exception {
		HtmlButton giro = (HtmlButton) portaria.getElementById("botao-giro");
		assertThat(giro.isDisabled()).as("o giro continua disponível").isFalse();

		giro.click();
		esperar(Duration.ofMillis(800));

		assertThat(texto(portaria, "painel-funcionario")).contains("Entrada registrada às 07:02");
		assertThat(texto(portaria, "monitor-aluno")).isEqualTo("Ana, entrada registrada.");
	}

	/** Entra pelo formulário de login, que leva à portaria. */
	private HtmlPage abrir() throws Exception {
		HtmlPage login = navegador.getPage("http://localhost/login");
		((HtmlInput) login.getElementById("usuario")).setValue(FUNCIONARIO);
		((HtmlInput) login.getElementById("senha")).setValue(SENHA);
		HtmlPage portaria = ((HtmlButton) login.querySelector("main form button[type=submit]")).click();
		assertThat(portaria.getUrl().getPath()).isEqualTo(SegurancaConfig.TELA_INICIAL);
		return portaria;
	}

	private void simularLeituraDaAna(HtmlPage portaria) throws Exception {
		((HtmlButton) portaria.querySelector("[data-codigo='" + CODIGO_ANA + "']")).click();
		esperar(Duration.ofMillis(800));
	}

	private static byte[] imagemPng() throws IOException {
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", saida);
		return saida.toByteArray();
	}

	private void esperar(Duration tempo) {
		navegador.waitForBackgroundJavaScript(tempo.toMillis());
	}

	private static String texto(HtmlPage pagina, String id) {
		DomNode regiao = pagina.getElementById(id);
		return regiao.getTextContent().replaceAll("\\s+", " ").trim();
	}

}
