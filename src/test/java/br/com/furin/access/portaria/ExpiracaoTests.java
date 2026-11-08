package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Expiração e cancelamento da liberação (T-29; RF09, fluxos A1 e A2; RN02, itens 4 e 5;
 * CA-RF09-04 e 05). O relógio é controlado: o teste avança 15 s em vez de esperar. A
 * tarefa agendada fica desligada no perfil {@code test}; a expiração é chamada direto ou
 * acontece no começo de cada operação. Roda com o perfil {@code prototipo} para ter o
 * giro simulado, sem a carga fictícia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ExpiracaoTests {

	private static final String CODIGO_ANA = "f1b2c3d4e5f60718293a4b5c6d7e8f29";
	private static final String CODIGO_BIA = "f2b2c3d4e5f60718293a4b5c6d7e8f29";
	private static final String FUNCIONARIO = "expiracao.teste";

	private static final Duration PRAZO = Duration.ofSeconds(15);

	/** 20/10/2026, 07:00 no horário da escola. */
	private static final Instant INICIO = LocalDateTime.of(2026, 10, 20, 7, 0)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(INICIO);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Expiracoes expiracoes;

	@Autowired
	private ApplicationContext contexto;

	private long vinculoAna;
	private long vinculoBia;

	@BeforeEach
	void preparar() {
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant ontem = relogio.instant().minus(Duration.ofDays(1));
		cena.funcionario(FUNCIONARIO, ontem);
		long turma = cena.turma("8C-T29");
		long ana = cena.aluno("T29-001", "Ana Teste", true, ontem);
		vinculoAna = cena.vinculo(ana, turma, ontem.minus(Duration.ofDays(30)), null);
		cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
		long bia = cena.aluno("T29-002", "Bia Teste", true, ontem);
		vinculoBia = cena.vinculo(bia, turma, ontem.minus(Duration.ofDays(30)), null);
		cena.carteirinha(bia, CODIGO_BIA, ontem, null, null);
	}

	// --- Expiração (RF09, fluxo A1; CA-RF09-04) ---

	@Test
	void semGiroEmQuinzeSegundosALiberacaoViraCanceladaComSemGiro(CapturedOutput saida) throws Exception {
		long liberacao = ler(CODIGO_ANA);
		int notificacoes = notificacoes();
		avancar(PRAZO);
		Instant agora = relogio.instant();

		assertThat(expiracoes.expirarVencidas()).isEqualTo(1);

		assertThat(registro(liberacao))
				.containsEntry("situacao", "CANCELADA")
				.containsEntry("motivo_cancelamento", "SEM_GIRO")
				.containsEntry("cancelado_em", CenaDaPortaria.utc(agora))
				.containsEntry("ocorrido_em", null);
		// Histórico, presentes e notificações só olham CONFIRMADA (arquitetura §5.4)
		assertThat(confirmadas()).isZero();
		assertThat(notificacoes()).isEqualTo(notificacoes);
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=LIBERACAO_EXPIRADA")))
				.singleElement().asString().contains("liberacao=" + liberacao, "motivo=SEM_GIRO");
	}

	@Test
	void antesDoPrazoALiberacaoContinuaPendente() throws Exception {
		long liberacao = ler(CODIGO_ANA);
		avancar(PRAZO.minusMillis(1));

		assertThat(expiracoes.expirarVencidas()).isZero();
		assertThat(registro(liberacao)).containsEntry("situacao", "AGUARDANDO_GIRO");
	}

	@Test
	void giroDepoisDoPrazoEhRecusadoEALiberacaoFicaExpirada() throws Exception {
		// Arquitetura §3.1: a verificação roda antes de cada operação, sem a tarefa agendada
		long liberacao = ler(CODIGO_ANA);
		avancar(PRAZO.plusSeconds(1));

		girar(liberacao, UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));

		// A recusa do giro não desfez a expiração
		assertThat(registro(liberacao)).containsEntry("situacao", "CANCELADA")
				.containsEntry("motivo_cancelamento", "SEM_GIRO").containsEntry("ocorrido_em", null);
	}

	@Test
	void depoisDoPrazoOutroAlunoPodeSerLiberado() throws Exception {
		// RN05: a liberação vencida da Ana não segura mais a portaria
		long ana = ler(CODIGO_ANA);
		avancar(PRAZO);

		lerComChave(CODIGO_BIA).andExpect(status().isCreated());

		assertThat(registro(ana)).containsEntry("motivo_cancelamento", "SEM_GIRO");
	}

	@Test
	void depoisDoPrazoANovaLeituraDaMesmaAlunaCriaOutraLiberacao() throws Exception {
		// RN04 vale só enquanto a liberação está pendente: a vencida não volta
		long primeira = ler(CODIGO_ANA);
		avancar(PRAZO);

		long segunda = ler(CODIGO_ANA);

		assertThat(segunda).isNotEqualTo(primeira);
		assertThat(registro(primeira)).containsEntry("situacao", "CANCELADA");
		assertThat(registro(segunda)).containsEntry("situacao", "AGUARDANDO_GIRO").containsEntry("tipo", "ENTRADA");
	}

	@Test
	void consultaDepoisDoPrazoDizQueALiberacaoExpirou() throws Exception {
		// A tela usa esta resposta para "Liberação expirada — Ana não passou"
		long liberacao = ler(CODIGO_ANA);
		consultar(liberacao).andExpect(status().isOk()).andExpect(jsonPath("$.situacao").value("AGUARDANDO_GIRO"));
		avancar(PRAZO);

		consultar(liberacao).andExpect(status().isOk())
				.andExpect(jsonPath("$.situacao").value("CANCELADA"))
				.andExpect(jsonPath("$.motivoCancelamento").value("SEM_GIRO"))
				.andExpect(jsonPath("$.primeiroNome").value("Ana"))
				// RN17: nada além disso
				.andExpect(jsonPath("$.length()").value(4));
	}

	@Test
	void consultaDeLiberacaoQueNaoExisteDa404() throws Exception {
		consultar(999_999_999L).andExpect(status().isNotFound());
	}

	// --- Cancelamento pelo funcionário (RF09, fluxo A2; RN02, item 5) ---

	@Test
	void funcionarioCancelaALiberacaoPendente(CapturedOutput saida) throws Exception {
		long liberacao = ler(CODIGO_ANA);
		avancar(Duration.ofSeconds(3));
		Instant agora = relogio.instant();

		cancelar(liberacao).andExpect(status().isOk())
				.andExpect(jsonPath("$.situacao").value("CANCELADA"))
				.andExpect(jsonPath("$.motivoCancelamento").value("FUNCIONARIO"))
				.andExpect(jsonPath("$.primeiroNome").value("Ana"));

		assertThat(registro(liberacao)).containsEntry("situacao", "CANCELADA")
				.containsEntry("motivo_cancelamento", "FUNCIONARIO")
				.containsEntry("cancelado_em", CenaDaPortaria.utc(agora));
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=LIBERACAO_CANCELADA")))
				.singleElement().asString()
				.contains("liberacao=" + liberacao, "motivo=FUNCIONARIO", "usuario=e***");
	}

	@Test
	void cancelarDeNovoDevolveAMesmaSituacaoSemOutroRegistroNoLog(CapturedOutput saida) throws Exception {
		long liberacao = ler(CODIGO_ANA);
		cancelar(liberacao).andExpect(status().isOk());
		Object canceladoEm = registro(liberacao).get("cancelado_em");
		avancar(Duration.ofSeconds(2));

		cancelar(liberacao).andExpect(status().isOk()).andExpect(jsonPath("$.motivoCancelamento").value("FUNCIONARIO"));

		assertThat(registro(liberacao)).containsEntry("cancelado_em", canceladoEm);
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=LIBERACAO_CANCELADA"))).hasSize(1);
	}

	@Test
	void cancelarDepoisDoPrazoMostraQueElaJaTinhaExpirado() throws Exception {
		// A expiração vem antes: a liberação vencida não vira cancelamento do funcionário
		long liberacao = ler(CODIGO_ANA);
		avancar(PRAZO);

		cancelar(liberacao).andExpect(status().isOk()).andExpect(jsonPath("$.motivoCancelamento").value("SEM_GIRO"));
	}

	@Test
	void movimentacaoConfirmadaNuncaEhCancelada() throws Exception {
		// RN02, item 5: a confirmada não é editada
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao, UUID.randomUUID()).andExpect(status().isOk());
		Map<String, Object> antes = registro(liberacao);

		cancelar(liberacao).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_CONFIRMADA"));

		assertThat(registro(liberacao)).isEqualTo(antes);
	}

	@Test
	void cancelarLiberacaoQueNaoExisteEhRecusado() throws Exception {
		cancelar(999_999_999L).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_NAO_ENCONTRADA"));
	}

	@Test
	void cancelarSemTokenCsrfEhRecusado() throws Exception {
		long liberacao = ler(CODIGO_ANA);

		mvc.perform(post("/portaria/liberacoes/" + liberacao + "/cancelar").with(funcionario()))
				.andExpect(status().isForbidden());

		assertThat(registro(liberacao)).containsEntry("situacao", "AGUARDANDO_GIRO");
	}

	// --- Giro em liberação encerrada (CA-RF09-05) ---

	@Test
	void giroNovoEmLiberacaoExpiradaEhRecusadoSemAlterarNada() throws Exception {
		long liberacao = ler(CODIGO_ANA);
		avancar(PRAZO);
		expiracoes.expirarVencidas();
		Map<String, Object> antes = registro(liberacao);
		avancar(Duration.ofSeconds(1));

		girar(liberacao, UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));

		assertThat(registro(liberacao)).isEqualTo(antes);
	}

	@Test
	void giroNovoEmLiberacaoCanceladaPeloFuncionarioEhRecusadoSemAlterarNada() throws Exception {
		long liberacao = ler(CODIGO_ANA);
		cancelar(liberacao).andExpect(status().isOk());
		Map<String, Object> antes = registro(liberacao);

		girar(liberacao, UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));

		assertThat(registro(liberacao)).isEqualTo(antes);
	}

	@Test
	void giroNovoEmLiberacaoConfirmadaEhRecusadoSemAlterarNada() throws Exception {
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao, UUID.randomUUID()).andExpect(status().isOk());
		Map<String, Object> antes = registro(liberacao);
		avancar(PRAZO.plusSeconds(5));

		girar(liberacao, UUID.randomUUID()).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));

		// A expiração nunca toca uma confirmada
		assertThat(registro(liberacao)).isEqualTo(antes);
	}

	// --- Tarefa agendada ---

	@Test
	void tarefaAgendadaFicaDesligadaNosTestes() {
		// furin.agendamentos.ativos=false (application-test.yml)
		assertThat(contexto.getBeanNamesForType(ExpiracaoAgendada.class)).isEmpty();
	}

	@Test
	void tarefaAgendadaChamaAExpiracao() {
		Expiracoes servico = mock(Expiracoes.class);

		new ExpiracaoAgendada(servico).expirar();

		verify(servico).expirarVencidas();
	}

	// --- Apoio ---

	private long ler(String codigo) throws Exception {
		String json = lerComChave(codigo).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.replaceAll(".*\"idLiberacao\":(\\d+).*", "$1"));
	}

	private ResultActions lerComChave(String codigo) throws Exception {
		return mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"));
	}

	private ResultActions girar(long liberacao, UUID chave) throws Exception {
		return mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idLiberacao\":" + liberacao + ",\"chaveRequisicao\":\"" + chave + "\"}"));
	}

	private ResultActions cancelar(long liberacao) throws Exception {
		return mvc.perform(post("/portaria/liberacoes/" + liberacao + "/cancelar").with(funcionario()).with(csrf()));
	}

	private ResultActions consultar(long liberacao) throws Exception {
		return mvc.perform(get("/portaria/liberacoes/" + liberacao).with(funcionario()));
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private Map<String, Object> registro(long id) {
		return jdbc.queryForMap("SELECT * FROM registro_acesso WHERE id_registro = ?", id);
	}

	private int confirmadas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo IN (?, ?) "
				+ "AND situacao = 'CONFIRMADA'", Integer.class, vinculoAna, vinculoBia);
	}

	private int notificacoes() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notificacao", Integer.class);
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

}
