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

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.TipoMovimentacao;

/**
 * Histórico (T-35; RF18, RF19; CA-RF18-01 a 09, CA-RF19-01 a 10, CA-RF12-08 e CA-RF21-12).
 * O relógio está parado em 10/03/2026, 12:00 no horário da escola: uma data que nenhum outro
 * teste usa, para o filtro "todas as turmas" ver só esta cena. Os dados vão direto no banco,
 * na transação do teste. Só dados fictícios.
 * <p>
 * A cena: Ana estava na 6A até 10/03 às 10:00 e passou para a 6B (A14 p.6). Bia, da 6A, tem o
 * aviso desligado. Davi, da 6A, está inativo e tem o aviso pendente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HistoricoTests {

	private static final String FUNCIONARIO = "historico.teste";

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

	private CenaDaPortaria cena;
	private long idFuncionario;
	private long turmaA;
	private long turmaB;
	private long anaNaA;
	private long anaNaB;
	private long carteirinhaAna;
	private long contatoAna;
	private long biaNaA;
	private long carteirinhaBia;
	private long daviNaA;
	private long carteirinhaDavi;
	private long contatoDavi;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant antes = AGORA.minus(Duration.ofDays(60));
		idFuncionario = cena.funcionario(FUNCIONARIO, antes);
		turmaA = cena.turma("6A-T35");
		turmaB = cena.turma("6B-T35");

		long ana = cena.aluno("T35-001", "Ana Teste", true, antes);
		Instant transferencia = naEscola(10, 10, 0);
		anaNaA = cena.vinculo(ana, turmaA, antes, transferencia);
		anaNaB = cena.vinculo(ana, turmaB, transferencia, null);
		carteirinhaAna = cena.carteirinha(ana, "a35b2c3d4e5f60718293a4b5c6d7e8f1", antes, null, null);
		contatoAna = contato(ana, "Carla Teste", "carla.teste@example.com", true);

		long bia = cena.aluno("T35-002", "Bia Teste", true, antes);
		biaNaA = cena.vinculo(bia, turmaA, antes, null);
		carteirinhaBia = cena.carteirinha(bia, "b35b2c3d4e5f60718293a4b5c6d7e8f2", antes, null, null);
		contato(bia, "Bruna Teste", "bruna.teste@example.com", false);

		long davi = cena.aluno("T35-003", "Davi Teste", false, antes);
		daviNaA = cena.vinculo(davi, turmaA, antes, null);
		carteirinhaDavi = cena.carteirinha(davi, "d35b2c3d4e5f60718293a4b5c6d7e8f3", antes, null, null);
		contatoDavi = contato(davi, "Carla Teste", "carla.teste@example.com", true);

		// 09/03: Ana entra às 07:02 e sai às 23:50 (hora local; 02:50 de 10/03 em UTC), na 6A
		notificacao(cena.confirmada(anaNaA, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA,
				naEscola(9, 7, 2)), contatoAna, "SIMULADA");
		notificacao(cena.confirmada(anaNaA, idFuncionario, carteirinhaAna, TipoMovimentacao.SAIDA,
				naEscola(9, 23, 50)), contatoAna, "SIMULADA");
		// 10/03: Bia (aviso desligado) às 07:10, Davi às 07:20, Ana na 6B às 11:00 e a saída manual
		cena.confirmada(biaNaA, idFuncionario, carteirinhaBia, TipoMovimentacao.ENTRADA, naEscola(10, 7, 10));
		notificacao(cena.confirmada(daviNaA, idFuncionario, carteirinhaDavi, TipoMovimentacao.ENTRADA,
				naEscola(10, 7, 20)), contatoDavi, "PENDENTE");
		notificacao(cena.confirmada(anaNaB, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA,
				naEscola(10, 11, 0)), contatoAna, "SIMULADA");
		notificacao(manual(anaNaB, TipoMovimentacao.SAIDA, naEscola(10, 11, 30), naEscola(10, 11, 45),
				"<script>alert('x')</script> Saiu com a mãe"), contatoAna, "SIMULADA");
		// Não aparecem: a cancelada de Bia e a pendente de Bia (RN12; DEC-19)
		cena.cancelada(biaNaA, idFuncionario, carteirinhaBia, TipoMovimentacao.SAIDA, naEscola(10, 9, 0));
		cena.pendente(biaNaA, idFuncionario, carteirinhaBia, TipoMovimentacao.SAIDA, naEscola(10, 11, 59));
	}

	// --- Estrutura e filtros (RF18, passo 2; CA-RF18-09) ---

	@Test
	void telaTemOsFiltrosComLabelNaOrdemDeFocoEOPadraoHoje() throws Exception {
		Pagina pagina = historico(get("/historico"));

		assertThat(pagina.titulo()).isEqualTo("Histórico | Furin Access");
		// A11 §5.1.4: data inicial, data final, turma, aluno, Filtrar
		assertThat(pagina.ids("#filtros input, #filtros select, #filtros button"))
				.containsExactly("inicio", "fim", "turma", "aluno", "");
		assertThat(pagina.texto("#filtros button")).isEqualTo("Filtrar");
		for (String campo : List.of("inicio", "fim", "turma", "aluno")) {
			assertThat(pagina.todos("label[for=" + campo + "]")).as("label de %s", campo).hasSize(1);
		}
		assertThat(pagina.texto("label[for=inicio]")).isEqualTo("Data inicial");
		assertThat(pagina.texto("label[for=fim]")).isEqualTo("Data final");
		assertThat(pagina.texto("label[for=turma]")).isEqualTo("Turma");
		assertThat(pagina.texto("label[for=aluno]")).isEqualTo("Aluno");
		// DEC-28, C5: padrão hoje, todas as turmas, aluno vazio
		assertThat(pagina.atributo("#inicio", "value")).isEqualTo("2026-03-10");
		assertThat(pagina.atributo("#fim", "value")).isEqualTo("2026-03-10");
		assertThat(pagina.texto("#turma option[selected]")).isEqualTo("Todas");
		assertThat(pagina.textos("#turma option")).contains("6A-T35 — Manhã (2026)", "6B-T35 — Manhã (2026)");
		assertThat(pagina.atributo("#aluno", "value")).isEmpty();
		// RF18, passo 6: o total é anunciado sem mover o foco. <output> tem o papel status implícito
		assertThat(pagina.todos("output#total-historico")).hasSize(1);
		assertThat(pagina.texto("#total-historico")).isEqualTo("4 registros encontrados");
	}

	@Test
	void hojeEmTodasAsTurmasMostraSoAsConfirmadasDaMaisRecenteParaAMaisAntiga() throws Exception {
		// CA-RF18-01; RF18, passo 4
		Pagina pagina = historico(get("/historico"));

		assertThat(pagina.textos("#tabela-historico tbody th")).containsExactly("Ana Teste (T35-001)",
				"Ana Teste (T35-001)", "Davi Teste (T35-003)", "Bia Teste (T35-002)");
		assertThat(pagina.textos("#tabela-historico tbody td:nth-of-type(2)"))
				.containsExactly("11:30", "11:00", "07:20", "07:10");
		// CA-RF19-08: a cancelada (09:00) e a pendente (11:59) de Bia não aparecem
		assertThat(pagina.texto("#tabela-historico")).doesNotContain("09:00", "11:59");
	}

	@Test
	void filtroPorTurmaUsaATurmaDoDiaDoEvento() throws Exception {
		// CA-RF18-02 e CA-RF19-04: Ana mudou para a 6B; as movimentações antigas ficam na 6A
		Pagina naA = historico(get("/historico").param("inicio", "2026-03-09").param("fim", "2026-03-10")
				.param("turma", String.valueOf(turmaA)));
		Pagina naB = historico(get("/historico").param("inicio", "2026-03-09").param("fim", "2026-03-10")
				.param("turma", String.valueOf(turmaB)));

		assertThat(naA.textos("#tabela-historico tbody th")).containsExactly("Davi Teste (T35-003)",
				"Bia Teste (T35-002)", "Ana Teste (T35-001)", "Ana Teste (T35-001)");
		assertThat(naA.textos("#tabela-historico tbody td:nth-of-type(3)")).containsOnly("6A-T35 — Manhã");
		assertThat(naB.textos("#tabela-historico tbody th")).containsOnly("Ana Teste (T35-001)").hasSize(2);
		assertThat(naB.textos("#tabela-historico tbody td:nth-of-type(3)")).containsOnly("6B-T35 — Manhã");
		assertThat(naB.atributo("#turma option[selected]", "value")).isEqualTo(String.valueOf(turmaB));
		assertThat(naA.texto("#tabela-historico caption"))
				.isEqualTo("Entradas e saídas de 09/03/2026 a 10/03/2026, turma 6A-T35 — Manhã (2026)");
	}

	@Test
	void filtroPorAlunoAceitaNomeOuMatricula() throws Exception {
		// CA-RF18-03
		Pagina porNome = historico(get("/historico").param("aluno", "ana teste"));
		Pagina porMatricula = historico(get("/historico").param("aluno", "T35-002"));
		Pagina soDigitos = historico(get("/historico").param("aluno", "35"));

		assertThat(porNome.textos("#tabela-historico tbody th")).containsOnly("Ana Teste (T35-001)").hasSize(2);
		assertThat(porNome.atributo("#aluno", "value")).isEqualTo("ana teste");
		assertThat(porMatricula.textos("#tabela-historico tbody th")).containsExactly("Bia Teste (T35-002)");
		// A matrícula vale inteira; um pedaço dela não acha ninguém (RF18, exceções: mesma mensagem)
		assertThat(soDigitos.texto("#total-historico"))
				.isEqualTo("Nenhuma entrada ou saída encontrada para os filtros escolhidos.");
		assertThat(soDigitos.todos("#tabela-historico")).isEmpty();
	}

	@Test
	void filtroPorMatriculaSoDeDigitosEhExato() throws Exception {
		// CA-RF18-03 com uma matrícula só de dígitos, como o "000123" do RF: comparada inteira
		jdbc.update("UPDATE aluno SET matricula = '000135' WHERE matricula = 'T35-002'");

		Pagina exata = historico(get("/historico").param("aluno", "000135"));
		Pagina parcial = historico(get("/historico").param("aluno", "0001"));

		assertThat(exata.textos("#tabela-historico tbody th")).containsExactly("Bia Teste (000135)");
		assertThat(parcial.todos("#tabela-historico")).isEmpty();
	}

	@Test
	void limitesDoDiaSeguemOFusoDaEscola() throws Exception {
		// CA-RF18-04 e CA-RF19-05: 23:50 de 09/03 (02:50 UTC de 10/03) fica em 09/03
		Pagina dia9 = historico(get("/historico").param("inicio", "09/03/2026").param("fim", "09/03/2026"));
		Pagina dia10 = historico(get("/historico").param("inicio", "2026-03-10").param("fim", "2026-03-10"));

		assertThat(dia9.textos("#tabela-historico tbody td:nth-of-type(2)")).containsExactly("23:50", "07:02");
		assertThat(dia9.textos("#tabela-historico tbody td:nth-of-type(1)")).containsOnly("09/03/2026");
		assertThat(dia10.texto("#tabela-historico")).doesNotContain("23:50");
		// O valor digitado em dd/mm/aaaa volta no formato do campo de data
		assertThat(dia9.atributo("#inicio", "value")).isEqualTo("2026-03-09");
	}

	// --- Erros dos filtros (RF18, exceções; CA-RF18-05) ---

	@Test
	void dataFinalAntesDaInicialEhRecusadaComAMensagemNoCampo() throws Exception {
		Pagina pagina = historico(get("/historico").param("inicio", "2026-03-10").param("fim", "2026-03-09"));

		String mensagem = "A data final precisa ser igual ou posterior à data inicial.";
		assertThat(pagina.atributo("#fim", "aria-invalid")).isEqualTo("true");
		assertThat(pagina.atributo("#fim", "aria-describedby")).contains("fim-erro");
		assertThat(pagina.texto("#fim-erro")).isEqualTo("Erro: " + mensagem);
		assertThat(pagina.texto("#resumo-erros a[href='#fim']")).isEqualTo(mensagem);
		assertThat(pagina.atributo("#inicio", "aria-invalid")).isEmpty();
		// Os filtros continuam preenchidos, e nada é consultado
		assertThat(pagina.atributo("#inicio", "value")).isEqualTo("2026-03-10");
		assertThat(pagina.atributo("#fim", "value")).isEqualTo("2026-03-09");
		assertThat(pagina.todos("#tabela-historico")).isEmpty();
	}

	@Test
	void periodoAcimaDe31DiasEDataInvalidaSaoRecusados() throws Exception {
		Pagina longo = historico(get("/historico").param("inicio", "2026-02-07").param("fim", "2026-03-10"));
		Pagina limite = historico(get("/historico").param("inicio", "2026-02-08").param("fim", "2026-03-10"));
		Pagina invalida = historico(get("/historico").param("inicio", "31/02/2026"));

		assertThat(longo.texto("#fim-erro")).isEqualTo("Erro: Escolha um período de até 31 dias.");
		assertThat(limite.todos("#fim-erro")).as("31 dias, contando os dois").isEmpty();
		assertThat(invalida.texto("#inicio-erro")).isEqualTo("Erro: Informe a data no formato dd/mm/aaaa.");
		assertThat(invalida.atributo("#inicio", "aria-invalid")).isEqualTo("true");
		assertThat(invalida.atributo("#inicio", "value")).isEqualTo("31/02/2026");
	}

	@Test
	void valoresManipuladosNaoCausamErroNemExpoemDados() throws Exception {
		// CA-RF18-06 (A10 §8.28): tudo vai como parâmetro, e o LIKE escapa % e _
		for (String aluno : List.of("' OR '1'='1", "%", "_", "Ana%' -- ", "!")) {
			Pagina pagina = historico(get("/historico").param("aluno", aluno));
			assertThat(pagina.todos("#tabela-historico")).as(aluno).isEmpty();
		}
		Pagina turma = historico(get("/historico").param("turma", "1 OR 1=1").param("pagina", "-5"));
		assertThat(turma.texto("#total-historico")).isEqualTo("4 registros encontrados");
		assertThat(turma.texto("#turma option[selected]")).isEqualTo("Todas");
		Pagina data = historico(get("/historico").param("inicio", "2026-03-10' OR 1=1 --"));
		assertThat(data.texto("#inicio-erro")).isEqualTo("Erro: Informe a data no formato dd/mm/aaaa.");
	}

	// --- Colunas (RF19) ---

	@Test
	void linhaDeLeituraTemTodasAsColunasEObservacaoVazia() throws Exception {
		// CA-RF19-01, 03, 05 e 07
		Pagina pagina = historico(get("/historico").param("aluno", "Bia"));

		assertThat(pagina.textos("#tabela-historico thead th")).containsExactly("Data", "Hora", "Aluno", "Turma",
				"Tipo", "Forma", "Observação", "Aviso ao responsável");
		assertThat(pagina.textos("#tabela-historico tbody tr:first-child > *")).containsExactly("10/03/2026", "07:10",
				"Bia Teste (T35-002)", "6A-T35 — Manhã", "Entrada", "Leitura", "", "Não enviado");
	}

	@Test
	void linhaManualMostraMotivoLancamentoEAutorComOHorarioInformado() throws Exception {
		// CA-RF19-02 e CA-RF12-08 (DEC-20): a Hora é a informada (11:30); o lançamento (11:45) vai na Observação
		Pagina pagina = historico(get("/historico").param("aluno", "Ana"));

		assertThat(pagina.textos("#tabela-historico tbody tr:first-child > *")).containsExactly("10/03/2026", "11:30",
				"Ana Teste (T35-001)", "6B-T35 — Manhã", "Saída", "Manual",
				"<script>alert('x')</script> Saiu com a mãe. Lançado às 11:45 por Funcionário Teste.", "Simulada");
	}

	@Test
	void motivoComScriptApareceComoTexto() throws Exception {
		// CA-RF19-09 (A10 §8.28): th:text escapa
		String html = mvc.perform(get("/historico").with(funcionario())).andExpect(status().isOk()).andReturn()
				.getResponse().getContentAsString();

		assertThat(html).contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; Saiu com a mãe")
				.doesNotContain("<script>alert");
	}

	@Test
	void tabelaTemCaptionECabecalhosAssociados() throws Exception {
		// CA-RF19-06 (A11 §5.6.3)
		Pagina pagina = historico(get("/historico"));

		assertThat(pagina.texto("#tabela-historico caption")).isEqualTo("Entradas e saídas de 10/03/2026, todas as turmas");
		assertThat(pagina.atributos("#tabela-historico thead th", "scope")).hasSize(8).containsOnly("col");
		assertThat(pagina.atributos("#tabela-historico tbody th", "scope")).hasSize(4).containsOnly("row");
	}

	@Test
	void tabelaNaoTemFotoContatoCodigoNemCanceladas() throws Exception {
		// CA-RF19-08 (A10 §5.34)
		String html = mvc.perform(get("/historico").param("inicio", "2026-03-09").with(funcionario()))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		Pagina pagina = new Pagina(html);

		assertThat(pagina.todos("main img")).isEmpty();
		assertThat(html).doesNotContain("example.com", "+55", "Carla", "Bruna", "a35b2c3d", "b35b2c3d", "d35b2c3d",
				".png", "CANCELADA", "AGUARDANDO_GIRO");
		assertThat(pagina.textos("#tabela-historico tbody th")).hasSize(6);
	}

	@Test
	void avisoAoResponsavelMostraOStatusDaNotificacao() throws Exception {
		// CA-RF19-10 e CA-RF21-12: Ana "Simulada", Bia (aviso desligado) "Não enviado", Davi "Pendente".
		// A versão impressa (RF20) é da T-36
		Pagina pagina = historico(get("/historico"));

		assertThat(pagina.textos("#tabela-historico tbody td:nth-of-type(7)"))
				.containsExactly("Simulada", "Simulada", "Pendente", "Não enviado");
	}

	@Test
	void alunoInativoContinuaNoHistorico() throws Exception {
		// RF19, exceções: o histórico é preservado (A14 p.5)
		Pagina pagina = historico(get("/historico").param("aluno", "Davi"));

		assertThat(pagina.textos("#tabela-historico tbody th")).containsExactly("Davi Teste (T35-003)");
	}

	// --- Paginação (RF18, passo 4) ---

	@Test
	void cinquentaPorPaginaComOsFiltrosNosLinks() throws Exception {
		for (int minuto = 0; minuto < 52; minuto++) {
			cena.confirmada(biaNaA, idFuncionario, carteirinhaBia, minuto % 2 == 0 ? TipoMovimentacao.ENTRADA
					: TipoMovimentacao.SAIDA, naEscola(5, 8, minuto));
		}

		Pagina primeira = historico(get("/historico").param("inicio", "2026-03-05").param("fim", "2026-03-05"));
		Pagina segunda = historico(get("/historico").param("inicio", "2026-03-05").param("fim", "2026-03-05")
				.param("pagina", "2"));
		Pagina alem = historico(get("/historico").param("inicio", "2026-03-05").param("fim", "2026-03-05")
				.param("pagina", "99"));

		assertThat(primeira.texto("#total-historico")).isEqualTo("52 registros encontrados");
		assertThat(primeira.todos("#tabela-historico tbody tr")).hasSize(50);
		assertThat(primeira.textos("#tabela-historico tbody td:nth-of-type(2)")).startsWith("08:51", "08:50");
		assertThat(primeira.texto("#tabela-historico caption")).endsWith("(página 1 de 2)");
		String proxima = primeira.atributo("nav.paginacao a", "href");
		assertThat(proxima).contains("inicio=2026-03-05", "fim=2026-03-05", "pagina=2");
		assertThat(segunda.textos("#tabela-historico tbody td:nth-of-type(2)")).containsExactly("08:01", "08:00");
		assertThat(segunda.textos("nav.paginacao a")).containsExactly("Página anterior");
		assertThat(alem.texto("#tabela-historico caption")).endsWith("(página 2 de 2)");
	}

	// --- Acesso (CA-RF18-07, CA-RF18-08, CA-RF21-12) ---

	@Test
	void ateEGestaoConsultamEATelaNaoTemAcaoDeAlteracao() throws Exception {
		for (String login : List.of("ate.demo", "gestao.demo")) {
			String html = mvc.perform(get("/historico").with(user(login).roles(SegurancaConfig.PERFIL_FUNCIONARIO)))
					.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			Pagina pagina = new Pagina(html);

			assertThat(pagina.textos("#tabela-historico tbody th")).hasSize(4);
			// Só o formulário GET dos filtros: nenhum POST, nenhum botão além de Filtrar, nenhum link de ação
			assertThat(pagina.todos("main form[method=post]")).isEmpty();
			assertThat(pagina.textos("main button")).containsExactly("Filtrar");
			assertThat(pagina.atributos("main a", "href")).allSatisfy(href -> assertThat(href)
					.matches("#.*|/historico\\?.*"));
		}
	}

	@Test
	void semLoginOHistoricoLevaAoLogin() throws Exception {
		mvc.perform(get("/historico")).andExpect(status().isFound()).andExpect(redirectedUrl("/login"));
	}

	// --- Apoio ---

	private Pagina historico(MockHttpServletRequestBuilder pedido) throws Exception {
		return new Pagina(mvc.perform(pedido.with(funcionario())).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString());
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private long contato(long aluno, String nome, String email, boolean recebe) {
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, ?, '+55 11 900000035', ?, 1, 1, ?)", aluno, nome, email, recebe);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private void notificacao(long registro, long contato, String status) {
		jdbc.update("INSERT INTO notificacao (id_registro, id_contato, canal, status, tentativas, criado_em) "
				+ "VALUES (?, ?, 'EMAIL', ?, 1, ?)", registro, contato, status, CenaDaPortaria.utc(AGORA));
	}

	private long manual(long vinculo, TipoMovimentacao tipo, Instant ocorrido, Instant lancado, String motivo) {
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao, motivo_manual) VALUES (?, ?, NULL, ?, 'MANUAL', "
				+ "'CONFIRMADA', ?, ?, ?, ?)", vinculo, idFuncionario, tipo.name(), CenaDaPortaria.utc(lancado),
				CenaDaPortaria.utc(ocorrido), UUID.randomUUID().toString(), motivo);
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

		List<String> ids(String css) {
			return todos(css).stream().map(DomElement::getId).toList();
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
