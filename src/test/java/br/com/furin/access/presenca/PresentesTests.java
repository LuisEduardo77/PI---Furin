package br.com.furin.access.presenca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.RegistroManual;
import br.com.furin.access.portaria.TipoMovimentacao;
import br.com.furin.access.presenca.PainelDePresentes.Painel;
import br.com.furin.access.presenca.PainelDePresentes.Pendente;
import br.com.furin.access.presenca.PainelDePresentes.Turma;

/**
 * Painel de presentes (T-34; RF16; RN12; CA-RF16-01 a 05 e 07 a 09). O relógio está parado
 * em 20/10/2026, 12:30 no horário da escola. O CA-RF16-06 (contador da portaria) está em
 * PortariaTelaTests, e a atualização da tela em PresentesTelaTests. Só dados fictícios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PresentesTests {

	private static final String FUNCIONARIO = "presentes.teste";

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

	@Autowired
	private PainelDePresentes painel;

	@Autowired
	private RegistroManual registroManual;

	private CenaDaPortaria cena;
	private long idFuncionario;
	private long turma6A;
	private long turma6B;
	private Instant antes;
	private int sequencia;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		antes = AGORA.minus(Duration.ofDays(60));
		idFuncionario = cena.funcionario(FUNCIONARIO, antes);
		turma6A = cena.turma("6A-T34");
		turma6B = cena.turma("6B-T34");
	}

	// --- CA-RF16-01: entradas e saídas confirmadas de hoje ---

	@Test
	void tresEntradasEUmaSaidaNa6AContamDoisEOTotalSomaAsTurmas() {
		Aluno ana = aluno("Ana Teste", turma6A);
		Aluno bia = aluno("Bia Teste", turma6A);
		Aluno carla = aluno("Carla Teste", turma6A);
		for (Aluno aluno : List.of(ana, bia, carla)) {
			confirmada(aluno, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));
		}
		confirmada(carla, TipoMovimentacao.SAIDA, naEscola(2026, 10, 20, 11, 0));

		Painel calculado = painel.calcular();

		assertThat(presentesNa(calculado, "6A-T34")).isEqualTo(2);
		assertThat(calculado.total()).isEqualTo(calculado.turmas().stream().mapToInt(Turma::presentes).sum());
		assertThat(calculado.atualizadoAs()).isEqualTo("12:30");
	}

	// --- CA-RF16-02: só CONFIRMADA conta ---

	@Test
	void liberacoesAguardandoGiroOuCanceladasNaoMudamAContagem() {
		Aluno davi = aluno("Davi Teste", turma6A);
		Aluno elisa = aluno("Elisa Teste", turma6A);
		Aluno fabio = aluno("Fábio Teste", turma6A);
		int totalAntes = painel.calcular().total();
		// Davi: só uma entrada cancelada. Fábio: entrada aguardando o giro
		cena.cancelada(davi.vinculo(), idFuncionario, davi.carteirinha(), TipoMovimentacao.ENTRADA,
				naEscola(2026, 10, 20, 7, 0));
		cena.pendente(fabio.vinculo(), idFuncionario, fabio.carteirinha(), TipoMovimentacao.ENTRADA, AGORA.minusSeconds(5));
		// Elisa entrou, e a saída foi cancelada sem giro: continua presente. (A RN05 deixa só
		// uma liberação pendente por vez no ponto de leitura, que já é a de Fábio)
		confirmada(elisa, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));
		cena.cancelada(elisa.vinculo(), idFuncionario, elisa.carteirinha(), TipoMovimentacao.SAIDA,
				naEscola(2026, 10, 20, 12, 0));

		Painel calculado = painel.calcular();

		assertThat(presentesNa(calculado, "6A-T34")).isEqualTo(1);
		assertThat(calculado.total()).isEqualTo(totalAntes + 1);
		assertThat(calculado.pendentes()).extracting(Pendente::nome).doesNotContain("Davi Teste", "Fábio Teste");
	}

	@Test
	void saidaAguardandoGiroNaoTiraOAlunoDosPresentes() {
		Aluno elisa = aluno("Elisa Teste", turma6A);
		confirmada(elisa, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));
		cena.pendente(elisa.vinculo(), idFuncionario, elisa.carteirinha(), TipoMovimentacao.SAIDA, AGORA.minusSeconds(3));

		assertThat(presentesNa(painel.calcular(), "6A-T34")).isEqualTo(1);
	}

	// --- CA-RF16-03 e 04: saídas pendentes de dias anteriores ---

	@Test
	void entradaDeDiaAnteriorSemSaidaFicaNasPendenciasEForaDoTotal() {
		Aluno ana = aluno("Ana Teste", turma6A);
		int totalAntes = painel.calcular().total();
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 2));

		Painel calculado = painel.calcular();

		assertThat(calculado.pendentes()).contains(new Pendente(ana.id(), "Ana Teste", "6A-T34 — Manhã", "16/10 07:02"));
		assertThat(presentesNa(calculado, "6A-T34")).isZero();
		assertThat(calculado.total()).isEqualTo(totalAntes);
	}

	@Test
	void saidaManualQueFechaAPendenciaTiraOAlunoDaLista() {
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 2));
		assertThat(painel.calcular().pendentes()).extracting(Pendente::idAluno).contains(ana.id());

		// RF12 (T-32): o registro manual da saída, como depois de "Registrar saída de Ana Teste"
		registroManual.registrar(ana.id(), naEscola(2026, 10, 16, 12, 30), "Saída não registrada no dia anterior",
				TipoMovimentacao.SAIDA, UUID.randomUUID(), FUNCIONARIO);

		Painel calculado = painel.calcular();
		assertThat(calculado.pendentes()).extracting(Pendente::idAluno).doesNotContain(ana.id());
		assertThat(presentesNa(calculado, "6A-T34")).isZero();
	}

	@Test
	void verificacaoDaRn12ComTresAlunos() {
		// RN12, "Verificação": entrada hoje; entrada em 16/10 sem saída; entrada e saída hoje
		Aluno ana = aluno("Ana Teste", turma6A);
		Aluno bia = aluno("Bia Teste", turma6A);
		Aluno carla = aluno("Carla Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));
		confirmada(bia, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 0));
		confirmada(carla, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 5));
		confirmada(carla, TipoMovimentacao.SAIDA, naEscola(2026, 10, 20, 12, 0));

		Painel calculado = painel.calcular();

		assertThat(presentesNa(calculado, "6A-T34")).isEqualTo(1);
		assertThat(calculado.pendentes()).extracting(Pendente::idAluno).containsOnlyOnce(bia.id())
				.doesNotContain(ana.id(), carla.id());
	}

	@Test
	void entradaPoucoAntesDaMeiaNoiteNaEscolaEhDeOntem() {
		// RN10: 19/10 às 23:59 na escola já é 20/10 em UTC, mas não é "hoje"
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 19, 23, 59));
		Aluno bia = aluno("Bia Teste", turma6A);
		confirmada(bia, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 0, 0));

		Painel calculado = painel.calcular();

		assertThat(calculado.pendentes()).extracting(Pendente::idAluno).contains(ana.id()).doesNotContain(bia.id());
		assertThat(presentesNa(calculado, "6A-T34")).isEqualTo(1);
	}

	// --- CA-RF16-05: vínculo vigente ---

	@Test
	void alunoTransferidoHojeDa6AParaA6BContaNa6B() {
		long id = cena.aluno("T34-T01", "Gabi Teste", true, antes);
		Instant transferencia = naEscola(2026, 10, 20, 10, 0);
		long vinculo6A = cena.vinculo(id, turma6A, antes, transferencia);
		cena.vinculo(id, turma6B, transferencia, null);
		long carteirinha = cena.carteirinha(id, codigo(), antes, null, null);
		// A entrada foi gravada no vínculo da 6A, antes da transferência
		cena.confirmada(vinculo6A, idFuncionario, carteirinha, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));

		Painel calculado = painel.calcular();

		assertThat(presentesNa(calculado, "6B-T34")).isEqualTo(1);
		assertThat(presentesNa(calculado, "6A-T34")).isZero();
	}

	// --- DEC-28, C3; dados utilizados ---

	@Test
	void turmasAtivasComZeroAparecemEAsInativasNao() {
		long inativa = cena.turma("9Z-T34");
		jdbc.update("UPDATE turma SET ativa = 0 WHERE id_turma = ?", inativa);

		Painel calculado = painel.calcular();

		assertThat(calculado.turmas()).extracting(Turma::nome).contains("6A-T34", "6B-T34").doesNotContain("9Z-T34");
		assertThat(presentesNa(calculado, "6B-T34")).isZero();
	}

	@Test
	void alunoInativoNaoContaNemApareceNasPendencias() {
		Aluno hugo = aluno("Hugo Teste", turma6A);
		Aluno iara = aluno("Iara Teste", turma6A);
		confirmada(hugo, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 0));
		confirmada(iara, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 0));
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno IN (?, ?)", hugo.id(), iara.id());

		Painel calculado = painel.calcular();

		assertThat(presentesNa(calculado, "6A-T34")).isZero();
		assertThat(calculado.pendentes()).extracting(Pendente::idAluno).doesNotContain(hugo.id(), iara.id());
	}

	// --- Tela (CA-RF16-07 a 09) ---

	@ParameterizedTest
	@ValueSource(strings = { "ate.demo", "gestao.demo" })
	void todoFuncionarioVeOPainelEORegistrarSaida(String login) throws Exception {
		// CA-RF16-07 (DEC-37): o mesmo painel e a mesma ação para os dois
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 2));

		HtmlPage pagina = html(login);

		DomElement acao = pagina.getElementById("registrar-saida-" + ana.id());
		assertThat(acao).as("o botão da pendência").isNotNull();
		assertThat(texto(acao)).isEqualTo("Registrar saída de Ana Teste");
		assertThat(acao.getAttribute("href")).isEqualTo("/portaria/manual/" + ana.id());
		assertThat(acao.getAttribute("class")).contains("botao");
		DomNode linha = acao.getParentNode().getParentNode();
		assertThat(linha.getNodeName()).isEqualTo("tr");
		assertThat(linha.querySelectorAll("th, td").stream().map(PresentesTests::texto).toList()).containsExactly("Ana Teste", "6A-T34 — Manhã", "16/10 07:02", "Registrar saída de Ana Teste");
	}

	@Test
	void semLoginOPainelLevaAoLogin() throws Exception {
		mvc.perform(get("/presentes")).andExpect(redirectedUrl("/login"));
	}

	@Test
	void painelTemTituloLegendaEAvisoDeQueNaoComprovaPresencaEmSala() throws Exception {
		// RF16, passos 1 e 3; CA-RF16-08 (A11 §5.12.12; A13 p.4)
		HtmlPage pagina = html("ate.demo");

		assertThat(pagina.getTitleText()).isEqualTo("Presentes | Furin Access");
		assertThat(pagina.querySelector("nav a[aria-current=page]").getTextContent()).isEqualTo("Presentes");
		assertThat(textos(pagina, "main h2")).containsExactly("Presentes agora", "Por turma", "Saídas pendentes");
		assertThat(texto(pagina.getElementById("legenda-presentes")))
				.isEqualTo("Presentes agora: alunos com entrada registrada hoje e sem saída registrada.");
		assertThat(texto(pagina.getElementById("aviso-presenca")))
				.isEqualTo("Este número não comprova presença em sala de aula.");
		assertThat(texto(pagina.getElementById("atualizado-em"))).isEqualTo("Atualizado às 12:30");
		assertThat(textos(pagina, "main button")).contains("Atualizar");
	}

	@Test
	void tabelaPorTurmaMostraTurmaTurnoEPresentes() throws Exception {
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 2));

		HtmlPage pagina = html("ate.demo");

		assertThat(textos(pagina, "#tabela-turmas thead th")).containsExactly("Turma", "Turno", "Presentes");
		assertThat(textos(pagina, "#tabela-turmas tbody tr")).contains("6A-T34 Manhã 1", "6B-T34 Manhã 0");
		assertThat(texto(pagina.getElementById("total-presentes")))
				.isEqualTo(String.valueOf(painel.calcular().total()));
	}

	@Test
	void tabelasTemThComScopeEOsNumerosNaoSaoRegiaoViva() throws Exception {
		// CA-RF16-09 (A11 §5.6.3; A11 p.9)
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 16, 7, 2));

		HtmlPage pagina = html("ate.demo");

		assertThat(pagina.querySelectorAll("main table")).hasSize(2);
		assertThat(pagina.querySelectorAll("main th")).isNotEmpty()
				.allSatisfy(th -> assertThat(((DomElement) th).getAttribute("scope")).isIn("col", "row"));
		assertThat(pagina.querySelectorAll("main table caption")).hasSize(2);
		// Só a região de anúncio é viva, e ela começa vazia (o "Atualizar" escreve nela)
		DomElement anuncio = pagina.getElementById("anuncio-presentes");
		assertThat(anuncio.getAttribute("role")).isEqualTo("status");
		assertThat(texto(anuncio)).isEmpty();
		assertThat(pagina.querySelectorAll("#dados-presentes [role], #dados-presentes [aria-live]")).isEmpty();
		assertThat(pagina.getElementById("dados-presentes").hasAttribute("aria-live")).isFalse();
		// O intervalo vem de furin.presentes.intervalo-atualizacao (30 s)
		assertThat(pagina.getElementById("presentes").getAttribute("data-intervalo-ms")).isEqualTo("30000");
	}

	@Test
	void semPresentesESemPendenciasMostraAsMensagens() throws Exception {
		// RF16, exceções. Só faz sentido com o banco de testes sem movimentação de hoje
		Painel calculado = painel.calcular();
		Assumptions.assumeTrue(calculado.total() == 0 && calculado.pendentes().isEmpty());

		HtmlPage pagina = html("ate.demo");

		assertThat(texto(pagina.querySelector("main"))).contains("Nenhum aluno com entrada registrada hoje.",
				"Não há saídas pendentes.");
		assertThat(pagina.getElementById("tabela-pendentes")).isNull();
	}

	@Test
	void portariaMostraOContadorResumido() throws Exception {
		Aluno ana = aluno("Ana Teste", turma6A);
		confirmada(ana, TipoMovimentacao.ENTRADA, naEscola(2026, 10, 20, 7, 2));
		String total = String.valueOf(painel.calcular().total());

		String html = mvc.perform(get("/portaria").with(funcionario("ate.demo"))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		HtmlPage pagina = carregar(html);

		assertThat(texto(pagina.getElementById("titulo-presentes"))).isEqualTo("Alunos presentes");
		assertThat(texto(pagina.getElementById("contador-presentes"))).isEqualTo(total);
		// Não é região viva: o painel do funcionário já anuncia o registro
		assertThat(pagina.getElementById("contador-presentes").getParentNode().getParentNode()
				.querySelectorAll("[role], [aria-live]")).isEmpty();
	}

	// --- Apoio ---

	private record Aluno(long id, long vinculo, long carteirinha) {
	}

	private Aluno aluno(String nome, long turma) {
		sequencia++;
		long id = cena.aluno("T34-" + String.format("%03d", sequencia), nome, true, antes);
		long vinculo = cena.vinculo(id, turma, antes, null);
		return new Aluno(id, vinculo, cena.carteirinha(id, codigo(), antes, null, null));
	}

	private void confirmada(Aluno aluno, TipoMovimentacao tipo, Instant ocorrido) {
		cena.confirmada(aluno.vinculo(), idFuncionario, aluno.carteirinha(), tipo, ocorrido);
	}

	private static String codigo() {
		return UUID.randomUUID().toString().replace("-", "");
	}

	private static int presentesNa(Painel calculado, String turma) {
		return calculado.turmas().stream().filter(linha -> turma.equals(linha.nome())).findFirst()
				.orElseThrow(() -> new AssertionError("turma " + turma + " não aparece no painel"))
				.presentes();
	}

	private HtmlPage html(String login) throws Exception {
		String html = mvc.perform(get("/presentes").with(funcionario(login))).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return carregar(html);
	}

	private static RequestPostProcessor funcionario(String login) {
		return user(login).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	/** A resposta lida pelo parser do HtmlUnit, sem JavaScript. */
	private static HtmlPage carregar(String html) throws Exception {
		// O cliente fica aberto: fechá-lo desmontaria a página antes das consultas
		WebClient cliente = new WebClient();
		cliente.getOptions().setJavaScriptEnabled(false);
		cliente.getOptions().setCssEnabled(false);
		return cliente.loadHtmlCodeIntoCurrentWindow(html);
	}

	private static List<String> textos(HtmlPage pagina, String css) {
		return pagina.querySelectorAll(css).stream().map(PresentesTests::texto).toList();
	}

	private static String texto(DomNode no) {
		return no.getTextContent().replaceAll("\\s+", " ").strip();
	}

	private static Instant naEscola(int ano, int mes, int dia, int hora, int minuto) {
		return LocalDateTime.of(ano, mes, dia, hora, minuto).atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();
	}

}
