package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Registro manual na portaria (T-32; RF12; RN07; CA-RF12-01 a 07, 09 e 10). O relógio está
 * parado em 20/10/2026, 12:30 no horário da escola. O CA-RF12-08 depende do histórico
 * (T-35).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ManualTests {

	private static final String FUNCIONARIO = "manual.teste";
	private static final String CODIGO_ANA = "c3b2c3d4e5f60718293a4b5c6d7e8f32";

	/** 20/10/2026, 12:30 no horário da escola. */
	private static final Instant AGORA = naEscola(2026, 10, 20, 12, 30);

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(AGORA);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private CenaDaPortaria cena;
	private long idFuncionario;
	private long davi;
	private long vinculoDavi;
	private long ana;
	private long vinculoAna;
	private long carteirinhaAna;
	private long bia;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant antes = AGORA.minus(Duration.ofDays(60));
		idFuncionario = cena.funcionario(FUNCIONARIO, antes);
		long turma = cena.turma("7A-T32");
		davi = cena.aluno("T32-001", "Davi Teste", true, antes);
		vinculoDavi = cena.vinculo(davi, turma, antes, null);
		ana = cena.aluno("T32-002", "Ana Teste", true, antes);
		vinculoAna = cena.vinculo(ana, turma, antes, null);
		carteirinhaAna = cena.carteirinha(ana, CODIGO_ANA, antes, null, null);
		bia = cena.aluno("T32-003", "Bia Teste", false, antes);
		cena.vinculo(bia, turma, antes, null);
	}

	// --- Busca (RF12, passo 2; RF11) ---

	@Test
	void buscaMostraSoAlunosAtivosComAAcaoDeRegistrar() throws Exception {
		Pagina pagina = html(mvc.perform(get("/portaria/manual").param("termo", "Teste").with(funcionario()))
				.andExpect(status().isOk()));

		assertThat(pagina.select("tbody th").eachText()).contains("Ana Teste", "Davi Teste").doesNotContain("Bia Teste");
		Elemento acao = pagina.comTexto("a", "Registrar manualmente para Davi Teste");
		assertThat(acao).isNotNull();
		assertThat(acao.attr("href")).isEqualTo("/portaria/manual/" + davi);
		// CA-RF12-10: com a aparência e o tamanho de botão (44 px, EstiloBaseTests)
		assertThat(acao.hasClass("botao")).isTrue();
	}

	@Test
	void portariaTemOLinkDoRegistroManual() throws Exception {
		Pagina pagina = html(mvc.perform(get("/portaria").with(funcionario())).andExpect(status().isOk()));

		assertThat(pagina.select("a[href='/portaria/manual']").eachText()).contains("Registro manual");
	}

	// --- Formulário (RF12, passos 3 e 4) ---

	@Test
	void formularioMostraOAlunoEOTipoCalculadoComOHorarioAtual() throws Exception {
		Pagina pagina = html(mvc.perform(get("/portaria/manual/" + davi).with(funcionario())).andExpect(status().isOk()));

		assertThat(pagina.select("dl dd").eachText()).contains("Davi Teste", "T32-001", "7A-T32 — Manhã", "Entrada");
		assertThat(pagina.getElementById("ocorridoEm").val()).isEqualTo("2026-10-20T12:30");
		assertThat(pagina.select("label[for=motivo]").get(0).text()).isEqualTo("Motivo (obrigatório)");
		// RN01: não existe campo de tipo para o funcionário
		assertThat(pagina.select("[name=tipo], [name=tipoMostrado]")).isEmpty();
	}

	@Test
	void saidaPendenteDeDiaAnteriorGeraUmaSaida() throws Exception {
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 0));

		Pagina pagina = html(mvc.perform(get("/portaria/manual/" + ana).with(funcionario())));

		assertThat(pagina.select("dl dd").eachText()).contains("Saída");
	}

	@Test
	void alunoInativoNaoPodeTerRegistroManual() throws Exception {
		Pagina pagina = html(mvc.perform(get("/portaria/manual/" + bia).with(funcionario())).andExpect(status().isOk()));

		assertThat(pagina.getElementById("bloqueio").text())
				.contains("Não é possível registrar movimentação para este aluno.");
		assertThat(pagina.select("form[action$='/revisar']")).isEmpty();
	}

	@Test
	void alunoQueNaoExisteDa404() throws Exception {
		mvc.perform(get("/portaria/manual/999999999").with(funcionario())).andExpect(status().isNotFound());
	}

	// --- Revisão (RF12, passo 5; CA-RF12-07) ---

	@Test
	void revisaoMostraAlunoTipoHorarioEMotivoAntesDeGravar() throws Exception {
		Pagina pagina = html(revisar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha")
				.andExpect(view().name("portaria/manual-revisar")));

		assertThat(pagina.select("dl dd").eachText())
				.contains("Davi Teste", "Entrada manual", "20/10/2026 às 07:15", "Aluno esqueceu a carteirinha");
		assertThat(pagina.select("button").eachText()).contains("Confirmar", "Corrigir");
		assertThat(registros(vinculoDavi)).isZero();
	}

	@Test
	void corrigirVoltaAoFormularioComOsValores() throws Exception {
		Pagina pagina = html(mvc.perform(comFormulario(post("/portaria/manual/" + davi + "/corrigir"),
				"2026-10-20T07:15", "Aluno esqueceu a carteirinha", "ENTRADA", UUID.randomUUID().toString()))
				.andExpect(view().name("portaria/manual")));

		assertThat(pagina.getElementById("ocorridoEm").val()).isEqualTo("2026-10-20T07:15");
		assertThat(pagina.getElementById("motivo").text()).isEqualTo("Aluno esqueceu a carteirinha");
	}

	// --- Gravação (RF12, passo 6; CA-RF12-01 e 02) ---

	@Test
	void entradaManualDeDaviEhGravadaComoManualEConfirmada(CapturedOutput saida) throws Exception {
		// CA-RF12-01
		confirmar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha", "ENTRADA", UUID.randomUUID())
				.andExpect(redirectedUrl("/portaria/manual"))
				.andExpect(flash().attribute("registrado", "Entrada manual registrada para Davi às 07:15."));

		Map<String, Object> registro = unicoRegistro(vinculoDavi);
		assertThat(registro)
				.containsEntry("tipo", "ENTRADA")
				.containsEntry("forma", "MANUAL")
				.containsEntry("situacao", "CONFIRMADA")
				.containsEntry("id_carteirinha", null)
				.containsEntry("motivo_manual", "Aluno esqueceu a carteirinha")
				.containsEntry("ocorrido_em", CenaDaPortaria.utc(naEscola(2026, 10, 20, 7, 15)))
				.containsEntry("lancado_em", CenaDaPortaria.utc(AGORA));
		assertThat(((Number) registro.get("id_usuario")).longValue()).isEqualTo(idFuncionario);
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=REGISTRO_MANUAL")))
				.singleElement().asString().contains("aluno=" + davi, "tipo=ENTRADA")
				.doesNotContain("carteirinha");
	}

	@Test
	void saidaManualDeOntemFechaAPendenciaEANovaLeituraEhEntrada() throws Exception {
		// CA-RF12-02, com os dias da cena: ENTRADA em 16/10 sem saída
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 0));

		confirmar(ana, "2026-10-16T12:30", "Saída não registrada no dia anterior", "SAIDA", UUID.randomUUID())
				.andExpect(redirectedUrl("/portaria/manual"))
				.andExpect(flash().attribute("registrado", "Saída manual registrada para Ana em 16/10 às 12:30."));

		Map<String, Object> saida = jdbc.queryForMap("SELECT * FROM registro_acesso WHERE id_vinculo = ? "
				+ "AND forma = 'MANUAL'", vinculoAna);
		assertThat(saida).containsEntry("tipo", "SAIDA")
				.containsEntry("ocorrido_em", CenaDaPortaria.utc(naEscola(2026, 10, 16, 12, 30)))
				.containsEntry("lancado_em", CenaDaPortaria.utc(AGORA));

		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO_ANA + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.tipo")
						.value("ENTRADA"));
	}

	@Test
	void reenvioDaMesmaConfirmacaoGravaUmRegistroSo() throws Exception {
		// RN08: um duplo clique em Confirmar manda a mesma chave
		UUID chave = UUID.randomUUID();
		confirmar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha", "ENTRADA", chave)
				.andExpect(redirectedUrl("/portaria/manual"));

		confirmar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha", "ENTRADA", chave)
				.andExpect(redirectedUrl("/portaria/manual"))
				.andExpect(flash().attribute("registrado", "Entrada manual registrada para Davi às 07:15."));

		assertThat(registros(vinculoDavi)).isEqualTo(1);
	}

	@Test
	void tipoEnviadoPelaInterfaceNuncaEhGravado() throws Exception {
		// RN01: um campo "tipo" é ignorado; o tipo mostrado só é comparado
		mvc.perform(comFormulario(post("/portaria/manual/" + davi), "2026-10-20T07:15", "Leitor com defeito", "ENTRADA",
				UUID.randomUUID().toString()).param("tipo", "SAIDA"))
				.andExpect(redirectedUrl("/portaria/manual"));

		assertThat(unicoRegistro(vinculoDavi)).containsEntry("tipo", "ENTRADA");
	}

	@Test
	void situacaoQueMudouDesdeARevisaoVoltaAoFormulario() throws Exception {
		// RF12, exceções: a revisão mostrou SAIDA, mas Davi está fora da escola
		Pagina pagina = html(confirmar(davi, "2026-10-20T07:15", "Leitor com defeito", "SAIDA", UUID.randomUUID())
				.andExpect(view().name("portaria/manual")));

		assertThat(pagina.getElementById("resumo-erros").text()).contains("A situação de Davi mudou. Revise o registro.");
		assertThat(pagina.select("dl dd").eachText()).contains("Entrada");
		assertThat(registros(vinculoDavi)).isZero();
	}

	// --- Recusas (CA-RF12-03 a 06) ---

	@Test
	void semMotivoNadaEhGravadoEOCampoMostraOErro() throws Exception {
		// CA-RF12-03
		Pagina pagina = html(revisar(davi, "2026-10-20T07:15", "   ").andExpect(view().name("portaria/manual")));

		assertThat(pagina.getElementById("motivo-erro").text()).contains("Informe o motivo do registro manual.");
		assertThat(pagina.getElementById("motivo").attr("aria-invalid")).isEqualTo("true");
		assertThat(pagina.getElementById("motivo").attr("aria-describedby")).contains("motivo-erro");
		// A11 §5.9.9: os dados digitados continuam
		assertThat(pagina.getElementById("ocorridoEm").val()).isEqualTo("2026-10-20T07:15");

		confirmar(davi, "2026-10-20T07:15", "", "ENTRADA", UUID.randomUUID()).andExpect(view().name("portaria/manual"));
		assertThat(registros(vinculoDavi)).isZero();
	}

	@Test
	void motivoComMaisDe200CaracteresEhRecusado() throws Exception {
		Pagina pagina = html(revisar(davi, "2026-10-20T07:15", "a".repeat(201)));

		assertThat(pagina.getElementById("motivo-erro").text()).contains("O motivo pode ter até 200 caracteres.");
		revisar(davi, "2026-10-20T07:15", "a".repeat(200)).andExpect(view().name("portaria/manual-revisar"));
	}

	@Test
	void horarioNoFuturoEhRecusado() throws Exception {
		// CA-RF12-04: um minuto depois de agora
		Pagina pagina = html(revisar(davi, "2026-10-20T12:31", "Aluno esqueceu a carteirinha"));
		assertThat(pagina.getElementById("ocorridoEm-erro").text())
				.contains("O horário não pode ser posterior ao horário atual.");

		// Também na gravação, para um envio que não passou pela revisão
		confirmar(davi, "2026-10-20T12:31", "Aluno esqueceu a carteirinha", "ENTRADA", UUID.randomUUID())
				.andExpect(view().name("portaria/manual"))
				.andExpect(content().string(containsString("O horário não pode ser posterior ao horário atual.")));
		assertThat(registros(vinculoDavi)).isZero();
	}

	@Test
	void horarioIgualOuAnteriorAUltimaMovimentacaoEhRecusado() throws Exception {
		// CA-RF12-05: ENTRADA confirmada hoje às 07:00
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));

		for (String horario : List.of("2026-10-20T07:00", "2026-10-20T06:59", "2026-10-19T18:00")) {
			Pagina pagina = html(revisar(ana, horario, "Leitor com defeito"));
			assertThat(pagina.getElementById("ocorridoEm-erro").text())
					.contains("O horário precisa ser posterior à última movimentação registrada (20/10 às 07:00).");
		}
		confirmar(ana, "2026-10-20T07:00", "Leitor com defeito", "SAIDA", UUID.randomUUID())
				.andExpect(view().name("portaria/manual"));
		revisar(ana, "2026-10-20T07:01", "Leitor com defeito").andExpect(view().name("portaria/manual-revisar"));
		assertThat(registros(vinculoAna)).isEqualTo(1);
	}

	@Test
	void horarioInvalidoEhRecusado() throws Exception {
		Pagina pagina = html(revisar(davi, "20/10/2026 07:15", "Leitor com defeito"));

		assertThat(pagina.getElementById("ocorridoEm-erro").text()).contains("Informe a data e a hora no formato");
	}

	@Test
	void comLiberacaoPendenteORegistroManualEhRecusado() throws Exception {
		// CA-RF12-06; RN07, item 6
		cena.pendente(vinculoDavi, idFuncionario, cena.carteirinha(davi, "d3b2c3d4e5f60718293a4b5c6d7e8f32", AGORA
				.minus(Duration.ofDays(1)), null, null), TipoMovimentacao.ENTRADA, AGORA.minusSeconds(3));
		String mensagem = "Há uma liberação em andamento para Davi. Aguarde a passagem ou cancele a liberação.";

		Pagina formulario = html(mvc.perform(get("/portaria/manual/" + davi).with(funcionario())));
		assertThat(formulario.getElementById("bloqueio").text()).contains(mensagem);
		assertThat(formulario.getElementById("bloqueio").attr("role")).isEqualTo("alert");
		assertThat(formulario.select("form[action$='/revisar']")).isEmpty();

		confirmar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha", "ENTRADA", UUID.randomUUID())
				.andExpect(view().name("portaria/manual"))
				.andExpect(content().string(containsString(mensagem)));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ? AND forma = 'MANUAL'",
				Integer.class, vinculoDavi)).isZero();
	}

	// --- Acesso e teclado (CA-RF12-09 e 10) ---

	@Test
	void semLoginORegistroManualLevaAoLogin() throws Exception {
		mvc.perform(get("/portaria/manual")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/portaria/manual/" + davi)).andExpect(redirectedUrl("/login"));
		mvc.perform(post("/portaria/manual/" + davi).with(csrf()).param("ocorridoEm", "2026-10-20T07:15")
				.param("motivo", "x").param("tipoMostrado", "ENTRADA").param("chaveRequisicao", UUID.randomUUID().toString()))
				.andExpect(redirectedUrl("/login"));
		assertThat(registros(vinculoDavi)).isZero();
	}

	@Test
	void confirmacaoSemTokenCsrfEhRecusada() throws Exception {
		mvc.perform(post("/portaria/manual/" + davi).with(funcionario()).param("ocorridoEm", "2026-10-20T07:15")
				.param("motivo", "x").param("tipoMostrado", "ENTRADA").param("chaveRequisicao", UUID.randomUUID().toString()))
				.andExpect(status().isForbidden());
		assertThat(registros(vinculoDavi)).isZero();
	}

	@Test
	void telasUsamSoControlesNativosComTamanhoDeBotao() throws Exception {
		// CA-RF12-10: links e botões de formulário comuns, sem JavaScript para operar e sem
		// tabindex positivo, e os botões com a classe de 44 px (EstiloBaseTests)
		Pagina formulario = html(mvc.perform(get("/portaria/manual/" + davi).with(funcionario())));
		Pagina revisao = html(revisar(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha"));

		for (Pagina pagina : List.of(formulario, revisao)) {
			assertThat(pagina.select("main [onclick], main [tabindex]:not([tabindex='-1'])")).isEmpty();
			assertThat(pagina.select("main button")).allSatisfy(botao -> assertThat(botao.hasClass("botao")).isTrue());
		}
		assertThat(formulario.select("main button[type=submit]").eachText()).containsExactly("Revisar");
		assertThat(revisao.select("main form button[type=submit]").eachText()).containsExactly("Confirmar", "Corrigir");
		assertThat(revisao.comTexto("button", "Corrigir").attr("formaction"))
				.isEqualTo("/portaria/manual/" + davi + "/corrigir");
	}

	@Test
	void revisaoGuardaOsValoresNoFormularioSemCampoDeTipoEditavel() throws Exception {
		mvc.perform(revisarPedido(davi, "2026-10-20T07:15", "Aluno esqueceu a carteirinha"))
				.andExpect(model().attributeExists("ficha"))
				.andExpect(content().string(not(containsString("name=\"tipo\""))));
	}

	// --- Apoio ---

	private ResultActions revisar(long idAluno, String ocorridoEm, String motivo) throws Exception {
		return mvc.perform(revisarPedido(idAluno, ocorridoEm, motivo));
	}

	private MockHttpServletRequestBuilder revisarPedido(long idAluno, String ocorridoEm, String motivo) {
		return post("/portaria/manual/" + idAluno + "/revisar").with(funcionario()).with(csrf())
				.param("ocorridoEm", ocorridoEm).param("motivo", motivo);
	}

	private ResultActions confirmar(long idAluno, String ocorridoEm, String motivo, String tipoMostrado, UUID chave)
			throws Exception {
		return mvc.perform(comFormulario(post("/portaria/manual/" + idAluno), ocorridoEm, motivo, tipoMostrado,
				chave.toString()));
	}

	private MockHttpServletRequestBuilder comFormulario(MockHttpServletRequestBuilder pedido, String ocorridoEm,
			String motivo, String tipoMostrado, String chave) {
		return pedido.with(funcionario()).with(csrf()).param("ocorridoEm", ocorridoEm).param("motivo", motivo)
				.param("tipoMostrado", tipoMostrado).param("chaveRequisicao", chave);
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private static Pagina html(ResultActions resposta) throws Exception {
		return new Pagina(resposta.andReturn().getResponse().getContentAsString());
	}

	/** A resposta lida pelo parser do HtmlUnit, sem JavaScript, com seletores CSS. */
	static final class Pagina {

		private final HtmlPage pagina;

		Pagina(String html) throws Exception {
			// O cliente fica aberto: fechá-lo desmontaria a página antes das consultas
			WebClient cliente = new WebClient();
			cliente.getOptions().setJavaScriptEnabled(false);
			cliente.getOptions().setCssEnabled(false);
			pagina = cliente.loadHtmlCodeIntoCurrentWindow(html);
		}

		Selecao select(String css) {
			Selecao selecao = new Selecao();
			for (DomNode no : pagina.querySelectorAll(css)) {
				selecao.add(new Elemento((DomElement) no));
			}
			return selecao;
		}

		Elemento getElementById(String id) {
			DomElement elemento = pagina.getElementById(id);
			return elemento == null ? null : new Elemento(elemento);
		}

		/** O primeiro elemento da tag com exatamente este texto. */
		Elemento comTexto(String tag, String texto) {
			return select(tag).stream().filter(elemento -> elemento.text().equals(texto)).findFirst().orElse(null);
		}
	}

	static final class Selecao extends ArrayList<Elemento> {

		private static final long serialVersionUID = 1L;

		List<String> eachText() {
			return stream().map(Elemento::text).toList();
		}
	}

	record Elemento(DomElement elemento) {

		String text() {
			return elemento.getTextContent().replaceAll("\\s+", " ").strip();
		}

		String val() {
			return elemento.getAttribute("value");
		}

		String attr(String nome) {
			return elemento.hasAttribute(nome) ? elemento.getAttribute(nome) : "";
		}

		boolean hasClass(String classe) {
			return List.of(attr("class").split("\\s+")).contains(classe);
		}
	}

	private int registros(long vinculo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ?", Integer.class, vinculo);
	}

	private Map<String, Object> unicoRegistro(long vinculo) {
		return jdbc.queryForMap("SELECT * FROM registro_acesso WHERE id_vinculo = ?", vinculo);
	}

	private static Instant naEscola(int ano, int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(ano, mes, dia, hora, minuto).atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();
	}

}
