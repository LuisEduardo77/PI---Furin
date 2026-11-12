package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
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
 * Leituras repetidas ou fora de sequência (T-27; RF15; RN03, RN04, RN05, RN08;
 * CA-RF15-01 a 04 e 06 a 09). O relógio é controlado: a janela de 2 s
 * ({@code furin.portaria.janela-repeticao}) é medida no banco, a partir de
 * {@code lancado_em}. Roda com o perfil {@code prototipo} para ter o giro simulado, sem a
 * carga fictícia. A concorrência (CA-RF15-05) está em {@link LeituraConcorrenteTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RepeticaoTests {

	private static final String CODIGO_ANA = "c1b2c3d4e5f60718293a4b5c6d7e8f27";
	private static final String CODIGO_BIA = "d1b2c3d4e5f60718293a4b5c6d7e8f27";
	private static final String FUNCIONARIO = "repeticao.teste";

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

	private CenaDaPortaria cena;

	private long idFuncionario;
	private long ana;
	private long vinculoAna;
	private long carteirinhaAna;
	private long bia;
	private long vinculoBia;
	private long carteirinhaBia;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant ontem = relogio.instant().minus(Duration.ofDays(1));
		idFuncionario = cena.funcionario(FUNCIONARIO, ontem);
		long turma = cena.turma("7B-T27");
		ana = cena.aluno("T27-001", "Ana Teste", true, ontem);
		vinculoAna = cena.vinculo(ana, turma, ontem.minus(Duration.ofDays(30)), null);
		carteirinhaAna = cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
		bia = cena.aluno("T27-002", "Bia Teste", true, ontem);
		vinculoBia = cena.vinculo(bia, turma, ontem.minus(Duration.ofDays(30)), null);
		carteirinhaBia = cena.carteirinha(bia, CODIGO_BIA, ontem, null, null);
	}

	@AfterEach
	void sequenciaContinuaAlternada() {
		// CA-RF15-07: nenhum cenário deixa duas CONFIRMADA seguidas do mesmo tipo
		assertThat(cena.confirmadasRepetidas()).isZero();
	}

	// --- RN03: janela de 2 s ---

	@Test
	void segundaLeituraEmMeioSegundoEhIgnoradaComLeituraJaRecebida() throws Exception {
		// CA-RF15-01
		long liberacao = idDaLiberacao(ler(CODIGO_ANA).andExpect(status().isCreated()));
		avancar(Duration.ofMillis(500));

		// 202: ignorada, com a liberação que continua pendente para a tela manter
		ler(CODIGO_ANA).andExpect(status().isAccepted())
				.andExpect(jsonPath("$.idLiberacao").value(liberacao))
				.andExpect(jsonPath("$.length()").value(6));

		assertThat(registros(vinculoAna)).isEqualTo(1);
	}

	@Test
	void leituraNoLimiteDaJanelaAindaEhIgnorada() throws Exception {
		// "Em até 2 s": exatamente 2 s depois ainda está na janela
		ler(CODIGO_ANA).andExpect(status().isCreated());
		avancar(Duration.ofSeconds(2));

		ler(CODIGO_ANA).andExpect(status().isAccepted());
	}

	@Test
	void giroEncerraAJanelaEANovaLeituraLiberaOTipoOposto() throws Exception {
		// CA-RF15-02; DEC-02: leitura → giro em 1 s → nova leitura em 1,5 s
		long entrada = idDaLiberacao(ler(CODIGO_ANA).andExpect(status().isCreated()));
		avancar(Duration.ofSeconds(1));
		girar(entrada).andExpect(status().isOk());
		avancar(Duration.ofMillis(500));

		ler(CODIGO_ANA).andExpect(status().isCreated()).andExpect(jsonPath("$.tipo").value("SAIDA"));

		assertThat(registros(vinculoAna)).isEqualTo(2);
	}

	@Test
	void leituraNaJanelaJaCanceladaEhIgnoradaSemCorpo() throws Exception {
		// Arquitetura §3.1: vale qualquer situação diferente de CONFIRMADA. A expiração e o
		// cancelamento são da T-29; aqui a liberação cancelada é gravada direto
		cena.cancelada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, relogio.instant());
		avancar(Duration.ofSeconds(1));

		ler(CODIGO_ANA).andExpect(status().isAccepted()).andExpect(content().string(""));

		avancar(Duration.ofMillis(1001));
		ler(CODIGO_ANA).andExpect(status().isCreated());
	}

	// --- RN04: leitura repetida com liberação pendente ---

	@Test
	void leituraEmCincoSegundosSemGiroMostraAguardandoPassagemSemNovaLiberacao() throws Exception {
		// CA-RF15-03: nenhuma liberação nova, e o prazo não é renovado
		long liberacao = idDaLiberacao(ler(CODIGO_ANA).andExpect(status().isCreated()));
		Object lancadoEm = lancadoEm(liberacao);
		avancar(Duration.ofSeconds(5));

		ler(CODIGO_ANA).andExpect(status().isOk()).andExpect(jsonPath("$.idLiberacao").value(liberacao));

		assertThat(registros(vinculoAna)).isEqualTo(1);
		assertThat(lancadoEm(liberacao)).isEqualTo(lancadoEm);
	}

	// --- RN05: uma liberação por vez ---

	@Test
	void comAnaAguardandoALeituraDeBiaEhRecusada() throws Exception {
		// CA-RF15-04
		ler(CODIGO_ANA).andExpect(status().isCreated());
		avancar(Duration.ofSeconds(3));

		ler(CODIGO_BIA).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("OUTRA_LIBERACAO_PENDENTE"));

		assertThat(registros(vinculoBia)).isZero();
	}

	@Test
	void depoisDoGiroDeAnaBiaPodePassar() throws Exception {
		girar(idDaLiberacao(ler(CODIGO_ANA).andExpect(status().isCreated()))).andExpect(status().isOk());

		ler(CODIGO_BIA).andExpect(status().isCreated()).andExpect(jsonPath("$.primeiroNome").value("Bia"));
	}

	@Test
	void recusaPorOutraLiberacaoVaiParaOLog(CapturedOutput saida) throws Exception {
		// RN09: toda recusa vai para o log, só com os 4 primeiros caracteres do código
		ler(CODIGO_ANA).andExpect(status().isCreated());
		ler(CODIGO_BIA).andExpect(status().isUnprocessableContent());

		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=LEITURA_RECUSADA")))
				.singleElement().asString()
				.contains("codigo=d1b2***", "motivo=OUTRA_LIBERACAO_PENDENTE")
				.doesNotContain(CODIGO_BIA);
	}

	// --- RN08: reenvio ---

	@Test
	void mesmaChaveEnviadaDuasVezesGeraUmRegistroEAMesmaResposta() throws Exception {
		// CA-RF15-06: o reenvio vem antes da janela, então repete o 201 original
		UUID chave = UUID.randomUUID();
		String primeira = ler(CODIGO_ANA, chave).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		avancar(Duration.ofMillis(300));

		String segunda = ler(CODIGO_ANA, chave).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(segunda).isEqualTo(primeira);
		assertThat(registros(vinculoAna)).isEqualTo(1);
	}

	// --- Reforço no banco ---

	@Test
	void segundaLiberacaoPendenteParaOMesmoVinculoEhRecusadaPeloBanco() {
		// CA-RF15-08
		Instant agora = relogio.instant();
		cena.pendente(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, agora);

		assertThatThrownBy(() -> cena.pendente(vinculoAna, idFuncionario, carteirinhaAna,
				TipoMovimentacao.ENTRADA, agora))
				.isInstanceOf(DuplicateKeyException.class).hasMessageContaining("uk_registro_pendente");
	}

	@Test
	void segundaLiberacaoPendenteDeOutroAlunoEhRecusadaPeloBanco() {
		// DEC-46: uma pendente por vez no ponto de leitura, mesmo gravando direto
		Instant agora = relogio.instant();
		cena.pendente(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, agora);

		assertThatThrownBy(() -> cena.pendente(vinculoBia, idFuncionario, carteirinhaBia,
				TipoMovimentacao.ENTRADA, agora))
				.isInstanceOf(DuplicateKeyException.class).hasMessageContaining(Leituras.UMA_LIBERACAO_POR_VEZ);
	}

	// --- CA-RF15-09: nada muda além da resposta ---

	@Test
	void leiturasIgnoradasOuRecusadasNaoGravamNadaNemGeramNotificacao() throws Exception {
		long liberacao = idDaLiberacao(ler(CODIGO_ANA).andExpect(status().isCreated()));
		String antes = estadoDosRegistros();
		int notificacoes = notificacoes();

		avancar(Duration.ofMillis(500));
		ler(CODIGO_ANA).andExpect(status().isAccepted());
		avancar(Duration.ofSeconds(5));
		ler(CODIGO_ANA).andExpect(status().isOk());
		ler(CODIGO_BIA).andExpect(status().isUnprocessableContent());

		assertThat(estadoDosRegistros()).isEqualTo(antes);
		assertThat(notificacoes()).isEqualTo(notificacoes);
		// O painel de presentes (RF16) só conta CONFIRMADA: a liberação segue pendente
		assertThat(jdbc.queryForObject("SELECT situacao FROM registro_acesso WHERE id_registro = ?", String.class,
				liberacao)).isEqualTo("AGUARDANDO_GIRO");
	}

	// --- Apoio ---

	private ResultActions ler(String codigo) throws Exception {
		return ler(codigo, UUID.randomUUID());
	}

	private ResultActions ler(String codigo, UUID chave) throws Exception {
		return mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + chave + "\"}"));
	}

	private ResultActions girar(long liberacao) throws Exception {
		return mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idLiberacao\":" + liberacao + ",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"));
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private static long idDaLiberacao(ResultActions resposta) throws Exception {
		String json = resposta.andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.split("\"idLiberacao\":", 2)[1].split("\\D", 2)[0]);
	}

	private int registros(long vinculo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ?", Integer.class,
				vinculo);
	}

	private Object lancadoEm(long liberacao) {
		return jdbc.queryForObject("SELECT lancado_em FROM registro_acesso WHERE id_registro = ?",
				LocalDateTime.class, liberacao);
	}

	/** Tudo o que importa dos registros da Ana e da Bia, para comparar antes e depois. */
	private String estadoDosRegistros() {
		return jdbc.queryForList("SELECT id_registro, situacao, lancado_em, ocorrido_em FROM registro_acesso "
				+ "WHERE id_vinculo IN (?, ?) ORDER BY id_registro", vinculoAna, vinculoBia).toString();
	}

	private int notificacoes() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notificacao", Integer.class);
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

}
