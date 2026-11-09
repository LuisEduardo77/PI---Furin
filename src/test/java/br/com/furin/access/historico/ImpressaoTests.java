package br.com.furin.access.historico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.TipoMovimentacao;

/**
 * Versão para impressão do histórico (T-36; RF20; CA-RF20-01 a 07). O relógio está parado em
 * 10/03/2026, 12:00 no horário da escola, a mesma data própria do {@link HistoricoTests}. O
 * limite do relatório fica em 60 registros ({@code furin.relatorio.max-registros}) só neste
 * teste, para provar o limite e a falta de paginação (o histórico mostra 50 por página) sem
 * gravar mil linhas. Os dados vão direto no banco, na transação do teste.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "furin.relatorio.max-registros=60")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ImpressaoTests {

	private static final String FUNCIONARIO = "impressao.teste";
	private static final String NENHUM = "Nenhuma entrada ou saída encontrada para os filtros escolhidos.";

	/** 10/03/2026, 12:00 no horário da escola. */
	private static final Instant AGORA = naEscola(10, 12, 0);

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(AGORA);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeamentos;

	private CenaDaPortaria cena;
	private long idFuncionario;
	private long turma;
	private long vinculoAna;
	private long carteirinhaAna;
	private long vinculoBia;
	private long carteirinhaBia;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant antes = AGORA.minus(Duration.ofDays(60));
		idFuncionario = cena.funcionario(FUNCIONARIO, antes);
		turma = cena.turma("6A-T36");
		long ana = cena.aluno("T36-001", "Ana Teste", true, antes);
		vinculoAna = cena.vinculo(ana, turma, antes, null);
		carteirinhaAna = cena.carteirinha(ana, "a36b2c3d4e5f60718293a4b5c6d7e8f1", antes, null, null);
		long bia = cena.aluno("T36-002", "Bia Teste", true, antes);
		vinculoBia = cena.vinculo(bia, turma, antes, null);
		carteirinhaBia = cena.carteirinha(bia, "b36b2c3d4e5f60718293a4b5c6d7e8f2", antes, null, null);
		long contato = contato(ana);

		// 10/03: Ana entra às 07:02 (com aviso SIMULADA), Bia às 07:10 e Ana sai, manual, às 11:30
		long entrada = cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA,
				naEscola(10, 7, 2));
		jdbc.update("INSERT INTO notificacao (id_registro, id_contato, canal, status, tentativas, criado_em) "
				+ "VALUES (?, ?, 'EMAIL', 'SIMULADA', 1, ?)", entrada, contato, CenaDaPortaria.utc(AGORA));
		cena.confirmada(vinculoBia, idFuncionario, carteirinhaBia, TipoMovimentacao.ENTRADA, naEscola(10, 7, 10));
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao, motivo_manual) VALUES (?, ?, NULL, 'SAIDA', 'MANUAL', "
				+ "'CONFIRMADA', ?, ?, ?, 'Saiu com a mãe')", vinculoAna, idFuncionario,
				CenaDaPortaria.utc(naEscola(10, 11, 45)), CenaDaPortaria.utc(naEscola(10, 11, 30)),
				UUID.randomUUID().toString());
		// Não aparece: a cancelada
		cena.cancelada(vinculoBia, idFuncionario, carteirinhaBia, TipoMovimentacao.SAIDA, naEscola(10, 9, 0));
	}

	// --- CA-RF20-01: mesmos registros e filtros, sem paginação, até o limite ---

	@Test
	void mostraOsMesmosRegistrosDaConsultaComOsMesmosFiltros() throws Exception {
		Pagina consulta = pagina(get("/historico").param("aluno", "Ana").param("turma", String.valueOf(turma)));
		Pagina relatorio = pagina(get("/historico/impressao").param("aluno", "Ana")
				.param("turma", String.valueOf(turma)));

		assertThat(relatorio.titulo()).isEqualTo("Relatório de entradas e saídas | Furin Access");
		assertThat(relatorio.textos("#tabela-relatorio tbody th"))
				.containsExactlyElementsOf(consulta.textos("#tabela-historico tbody th")).hasSize(2);
		assertThat(relatorio.textos("#tabela-relatorio tbody td:nth-of-type(2)")).containsExactly("11:30", "07:02");
	}

	@Test
	void linkDoHistoricoLevaOsFiltrosSemAPagina() throws Exception {
		Pagina consulta = pagina(get("/historico").param("inicio", "2026-03-09").param("aluno", "Ana")
				.param("turma", String.valueOf(turma)).param("pagina", "1"));

		String link = consulta.atributo("#link-impressao", "href");
		assertThat(consulta.texto("#link-impressao")).isEqualTo("Versão para impressão");
		assertThat(link).startsWith("/historico/impressao?").contains("inicio=2026-03-09", "fim=2026-03-10",
				"turma=" + turma, "aluno=Ana").doesNotContain("pagina");
	}

	@Test
	void semPaginacaoAteOLimiteEAcimaDeleNadaEListado() throws Exception {
		// 60 no dia 05/03: o histórico divide em 50 + 10; o relatório traz os 60 de uma vez
		for (int minuto = 0; minuto < 60; minuto++) {
			cena.confirmada(vinculoBia, idFuncionario, carteirinhaBia,
					minuto % 2 == 0 ? TipoMovimentacao.ENTRADA : TipoMovimentacao.SAIDA, naEscola(5, 8, minuto));
		}
		MockHttpServletRequestBuilder do5 = get("/historico/impressao").param("inicio", "2026-03-05")
				.param("fim", "2026-03-05");

		Pagina noLimite = pagina(do5);
		assertThat(noLimite.todos("#tabela-relatorio tbody tr")).hasSize(60);
		assertThat(noLimite.texto("#total-do-relatorio")).isEqualTo("Total: 60 registros");
		assertThat(noLimite.todos("nav.paginacao, [href*='pagina=']")).isEmpty();

		cena.confirmada(vinculoBia, idFuncionario, carteirinhaBia, TipoMovimentacao.ENTRADA, naEscola(5, 9, 0));
		Pagina acima = pagina(get("/historico/impressao").param("inicio", "2026-03-05").param("fim", "2026-03-05"));
		assertThat(acima.texto("#excedeu"))
				.isEqualTo("Erro: O relatório tem mais de 60 registros. Refine os filtros para imprimir.");
		assertThat(acima.atributo("#excedeu", "role")).isEqualTo("alert");
		assertThat(acima.todos("#tabela-relatorio, #botao-imprimir")).isEmpty();
	}

	// --- CA-RF20-02: cabeçalho ---

	@Test
	void cabecalhoTemEscolaFiltrosEmissaoEmissorETotal() throws Exception {
		Pagina relatorio = pagina(get("/historico/impressao").param("inicio", "09/03/2026")
				.param("turma", String.valueOf(turma)).param("aluno", "Ana"));

		assertThat(relatorio.texto("h1")).isEqualTo("Furin Access — Relatório de entradas e saídas");
		// DEC-28, C7: furin.escola.nome, fictício no repositório
		assertThat(relatorio.texto("#nome-da-escola")).isEqualTo("Escola Exemplo");
		assertThat(relatorio.texto("#filtros-do-relatorio"))
				.isEqualTo("Filtros: de 09/03/2026 a 10/03/2026, turma 6A-T36 — Manhã (2026), aluno \"Ana\"");
		assertThat(relatorio.texto("#emissao")).isEqualTo("Emitido em 10/03/2026 às 12:00 por Funcionário Teste");
		assertThat(relatorio.texto("#total-do-relatorio")).isEqualTo("Total: 2 registros");
		assertThat(relatorio.texto("#uso-restrito"))
				.isEqualTo("Documento com dados pessoais de alunos. Uso restrito da escola.");
	}

	@Test
	void semRegistrosOCabecalhoSaiComAMensagem() throws Exception {
		Pagina relatorio = pagina(get("/historico/impressao").param("inicio", "2026-03-01").param("fim", "2026-03-01"));

		assertThat(relatorio.texto("#total-do-relatorio")).isEqualTo(NENHUM);
		assertThat(relatorio.texto("#emissao")).startsWith("Emitido em 10/03/2026");
		assertThat(relatorio.todos("#tabela-relatorio")).isEmpty();
	}

	@Test
	void filtroInvalidoNaoMontaORelatorioNemVaiParaOLog(CapturedOutput saida) throws Exception {
		Pagina relatorio = pagina(get("/historico/impressao").param("inicio", "2026-03-10").param("fim", "2026-03-09"));

		assertThat(relatorio.texto("#erros-do-relatorio"))
				.contains("A data final precisa ser igual ou posterior à data inicial.");
		assertThat(relatorio.todos("#tabela-relatorio, #botao-imprimir, #emissao")).isEmpty();
		assertThat(saida.getOut()).doesNotContain("evento=RELATORIO_IMPRESSO");
	}

	// --- Tabela: sem a coluna do aviso, tipo e forma em texto (RF19, RF21; CA-RF20-06) ---

	@Test
	void tabelaNaoTemOAvisoAoResponsavelETemTipoEFormaEmTexto() throws Exception {
		String html = html(get("/historico/impressao"));
		Pagina relatorio = new Pagina(html);

		assertThat(relatorio.textos("#tabela-relatorio thead th")).containsExactly("Data", "Hora", "Aluno", "Turma",
				"Tipo", "Forma", "Observação");
		assertThat(html).doesNotContain("Aviso ao responsável", "Simulada", "Não enviado", "example.com");
		assertThat(relatorio.textos("#tabela-relatorio tbody tr:first-child > *")).containsExactly("10/03/2026",
				"11:30", "Ana Teste (T36-001)", "6A-T36 — Manhã", "Saída", "Manual",
				"Saiu com a mãe. Lançado às 11:45 por Funcionário Teste.");
		assertThat(relatorio.textos("#tabela-relatorio tbody td:nth-of-type(4)")).containsOnly("Entrada", "Saída");
		assertThat(relatorio.textos("#tabela-relatorio tbody td:nth-of-type(5)")).containsOnly("Leitura", "Manual");
		// A cancelada (09:00) não aparece
		assertThat(relatorio.texto("#tabela-relatorio")).doesNotContain("09:00");
		assertThat(relatorio.atributos("#tabela-relatorio thead th", "scope")).containsOnly("col");
		assertThat(relatorio.atributos("#tabela-relatorio tbody th", "scope")).containsOnly("row");
	}

	// --- CA-RF20-03: o que some na impressão ---

	@Test
	void naImpressaoSomemMenuBotoesEFiltrosEOCabecalhoDaTabelaSeRepete() throws Exception {
		Pagina relatorio = pagina(get("/historico/impressao"));
		String css = mvc.perform(get("/css/base.css")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		String impressao = css.substring(css.lastIndexOf("@media print"));

		// O menu fica no cabeçalho do layout (.cabecalho), e as ações da página em .sem-impressao
		assertThat(relatorio.todos("header.cabecalho nav.menu")).hasSize(1);
		assertThat(relatorio.todos(".sem-impressao #botao-imprimir")).hasSize(1);
		assertThat(relatorio.todos("main form")).isEmpty();
		assertThat(impressao).contains(".pular-para-conteudo", ".cabecalho", ".sem-impressao", ".coluna-aviso",
				"button", "display: none", "thead {", "display: table-header-group", "break-inside: avoid",
				"background: #fff");
		// No histórico, o formulário de filtros, a paginação e a coluna do aviso também somem
		Pagina historico = pagina(get("/historico"));
		assertThat(historico.atributo("#filtros", "class")).contains("sem-impressao");
		assertThat(historico.atributos("#tabela-historico .coluna-aviso", "class")).hasSize(4);
	}

	// --- CA-RF20-04: acesso ---

	@Test
	void ateEGestaoImprimemESemLoginVaiAoLogin() throws Exception {
		for (String login : List.of("ate.demo", "gestao.demo")) {
			mvc.perform(get("/historico/impressao").with(user(login).roles(SegurancaConfig.PERFIL_FUNCIONARIO)))
					.andExpect(status().isOk());
		}
		mvc.perform(get("/historico/impressao")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
	}

	// --- CA-RF20-05: botão Imprimir ---

	@Test
	void botaoImprimirEhUmBotaoDeVerdadeComOTamanhoMinimo() throws Exception {
		Pagina relatorio = pagina(get("/historico/impressao"));
		String js = mvc.perform(get("/js/impressao.js")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		String css = mvc.perform(get("/css/base.css")).andReturn().getResponse().getContentAsString();

		// <button type="button">: foco pelo Tab, Enter e espaço sem código extra (A11 §5.8.6)
		DomElement botao = relatorio.todos("button#botao-imprimir").get(0);
		assertThat(botao.getAttribute("type")).isEqualTo("button");
		assertThat(botao.hasAttribute("tabindex")).isFalse();
		assertThat(botao.getAttribute("class")).contains("botao");
		assertThat(relatorio.texto("#botao-imprimir")).isEqualTo("Imprimir");
		// .botao: min-width e min-height de --alvo-minimo, 2.75rem = 44 px (EstiloBaseTests)
		assertThat(css).contains("--alvo-minimo: 2.75rem");
		assertThat(js).contains("getElementById('botao-imprimir')", "window.print()");
		assertThat(relatorio.atributos("script[src]", "src")).contains("/js/impressao.js");
	}

	// --- CA-RF20-07: sem exportação ---

	@Test
	void naoHaExportacaoParaCsvOuPlanilha() throws Exception {
		String html = html(get("/historico/impressao"));
		Pagina relatorio = new Pagina(html);

		assertThat(relatorio.todos("a[download]")).isEmpty();
		assertThat(html.toLowerCase()).doesNotContain(".csv", "text/csv", ".xls", "planilha", "exportar");
		Stream<String> caminhos = mapeamentos.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream());
		assertThat(caminhos).noneMatch(caminho -> caminho.matches("(?i).*(csv|xls|export|planilha|dre).*"));
	}

	// --- DEC-28, C6: log ---

	@Test
	void aImpressaoVaiParaOLogSemODadoDoAluno(CapturedOutput saida) throws Exception {
		html(get("/historico/impressao").param("inicio", "2026-03-09").param("turma", String.valueOf(turma))
				.param("aluno", "Ana Teste"));

		List<String> linhas = saida.getOut().lines().filter(linha -> linha.contains("evento=RELATORIO_IMPRESSO"))
				.toList();
		assertThat(linhas).singleElement().satisfies(linha -> assertThat(linha)
				.contains("usuario=i***", "inicio=2026-03-09", "fim=2026-03-10", "turma=" + turma, "aluno=FILTRADO",
						"registros=2")
				.doesNotContain("Ana", "T36-"));
	}

	// --- Apoio ---

	private Pagina pagina(MockHttpServletRequestBuilder pedido) throws Exception {
		return new Pagina(html(pedido));
	}

	/** Sem os comentários HTML, que explicam o código e citam, por exemplo, "CSV ou planilha". */
	private String html(MockHttpServletRequestBuilder pedido) throws Exception {
		return mvc.perform(pedido.with(funcionario())).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString().replaceAll("(?s)<!--.*?-->", "");
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private long contato(long aluno) {
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, 'Carla Teste', '+55 11 900000036', 'carla.teste@example.com', 1, 1, 1)",
				aluno);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private static Instant naEscola(int dia, int hora, int minuto) {
		return LocalDateTime.of(2026, 3, dia, hora, minuto).atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();
	}

	/** A resposta lida pelo parser do HtmlUnit, sem JavaScript, com seletores CSS. */
	private static final class Pagina {

		private final HtmlPage pagina;

		Pagina(String html) throws Exception {
			// O cliente fica aberto: fechá-lo desmontaria a página antes das consultas
			WebClient cliente = new WebClient();
			cliente.getOptions().setJavaScriptEnabled(false);
			cliente.getOptions().setCssEnabled(false);
			pagina = cliente.loadHtmlCodeIntoCurrentWindow(html);
		}

		String titulo() {
			return pagina.getTitleText();
		}

		List<DomElement> todos(String css) {
			List<DomElement> elementos = new ArrayList<>();
			for (DomNode no : pagina.querySelectorAll(css)) {
				elementos.add((DomElement) no);
			}
			return elementos;
		}

		String texto(String css) {
			DomNode no = pagina.querySelector(css);
			return no == null ? null : limpo(no.getTextContent());
		}

		List<String> textos(String css) {
			return todos(css).stream().map(elemento -> limpo(elemento.getTextContent())).toList();
		}

		String atributo(String css, String nome) {
			DomElement elemento = pagina.querySelector(css);
			return elemento == null || !elemento.hasAttribute(nome) ? "" : elemento.getAttribute(nome);
		}

		List<String> atributos(String css, String nome) {
			return todos(css).stream().map(elemento -> elemento.getAttribute(nome)).toList();
		}

		private static String limpo(String texto) {
			return texto.replaceAll("\\s+", " ").strip();
		}
	}

}
