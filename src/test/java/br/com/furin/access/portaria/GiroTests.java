package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
 * {@code POST /portaria/giros}, o giro simulado (T-28; RF09, RF14; RN02, passo 3;
 * CA-RF09-03 e 05). Roda com o perfil {@code prototipo}, onde a rota existe. A carga
 * fictícia (V2, só do {@code prototipo}) fica desligada com {@code furin.carga.ativa=false},
 * para não gravar dados no banco de testes. A rota fora do {@code prototipo} está em
 * {@link LeituraTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@Transactional
class GiroTests {

	private static final String CODIGO_ANA = "b1b2c3d4e5f60718293a4b5c6d7e8f91";
	private static final String FUNCIONARIO = "giro.teste";

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

	private long vinculoAna;
	private long carteirinhaAna;
	private long idFuncionario;

	@BeforeEach
	void preparar() {
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant ontem = relogio.instant().minus(Duration.ofDays(1));
		idFuncionario = cena.funcionario(FUNCIONARIO, ontem);
		long turma = cena.turma("6A-T28");
		long ana = cena.aluno("T28-001", "Ana Teste", true, ontem);
		vinculoAna = cena.vinculo(ana, turma, ontem.minus(Duration.ofDays(30)), null);
		carteirinhaAna = cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
	}

	@Test
	void giroConfirmaALiberacaoComOInstanteDoServidor() throws Exception {
		// CA-RF09-03: CONFIRMADA, com ocorrido_em = instante do giro (≥ lancado_em)
		long liberacao = ler();
		avancar(Duration.ofSeconds(3));
		Instant instanteDoGiro = relogio.instant();

		girar(liberacao)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idLiberacao").value(liberacao))
				.andExpect(jsonPath("$.situacao").value("CONFIRMADA"))
				.andExpect(jsonPath("$.tipo").value("ENTRADA"))
				.andExpect(jsonPath("$.primeiroNome").value("Ana"));

		Map<String, Object> registro = registro(liberacao);
		assertThat(registro).containsEntry("situacao", "CONFIRMADA")
				.containsEntry("ocorrido_em", CenaDaPortaria.utc(instanteDoGiro));
		assertThat((LocalDateTime) registro.get("ocorrido_em")).isAfter((LocalDateTime) registro.get("lancado_em"));
	}

	@Test
	void depoisDoGiroDaEntradaANovaLeituraLiberaASaida() throws Exception {
		// DEC-02 e RN01: o giro encerra a pendência, e a próxima leitura do mesmo dia é SAIDA
		girar(ler()).andExpect(status().isOk());
		avancar(Duration.ofHours(5));

		long saida = ler();
		girar(saida).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("SAIDA"));

		// E depois da saída confirmada, volta a ser ENTRADA
		avancar(Duration.ofMinutes(10));
		leitura(UUID.randomUUID()).andExpect(jsonPath("$.tipo").value("ENTRADA"));
	}

	@Test
	void reenvioDoGiroComAMesmaChaveDevolveOResultadoOriginal() throws Exception {
		// RN08; DEC-41; CA-RF09-12: o instante continua o do primeiro giro
		long liberacao = ler();
		UUID chave = UUID.randomUUID();
		String original = girar(liberacao, chave).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		Object primeiroGiro = registro(liberacao).get("ocorrido_em");
		avancar(Duration.ofSeconds(5));

		String reenvio = girar(liberacao, chave).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(reenvio).isEqualTo(original);
		assertThat(registro(liberacao)).containsEntry("ocorrido_em", primeiroGiro)
				.containsEntry("chave_giro", chave.toString());
	}

	@Test
	void giroComOutraChaveNaMesmaLiberacaoEhRecusadoSemAlterarNada() throws Exception {
		// CA-RF09-05: giro novo em liberação já confirmada
		long liberacao = ler();
		girar(liberacao).andExpect(status().isOk());
		Object primeiroGiro = registro(liberacao).get("ocorrido_em");
		avancar(Duration.ofSeconds(5));

		girar(liberacao).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));
		assertThat(registro(liberacao)).containsEntry("ocorrido_em", primeiroGiro);
	}

	@Test
	void giroEmLiberacaoCanceladaEhRecusadoSemAlterarNada() throws Exception {
		// CA-RF09-05: liberação cancelada (a expiração automática é da T-29)
		long cancelada = new CenaDaPortaria(jdbc).cancelada(vinculoAna, idFuncionario, carteirinhaAna,
				TipoMovimentacao.ENTRADA, relogio.instant());

		girar(cancelada).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_ENCERRADA"));
		assertThat(registro(cancelada)).containsEntry("situacao", "CANCELADA").containsEntry("ocorrido_em", null);
	}

	@Test
	void giroDeLiberacaoQueNaoExisteEhRecusado() throws Exception {
		girar(999_999_999L).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LIBERACAO_NAO_ENCONTRADA"));
	}

	@Test
	void cargaFicticiaNaoRodaNoBancoDeTestes() {
		// O perfil prototipo traria a V2 (carga fictícia); furin.carga.ativa=false a deixa de fora
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2'",
				Integer.class)).isZero();
	}

	@Test
	void giroSemIdSemChaveOuSemTokenCsrfEhRecusado() throws Exception {
		mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"idLiberacao\":1}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/portaria/giros").with(funcionario())
				.contentType(MediaType.APPLICATION_JSON).content("{\"idLiberacao\":1}"))
				.andExpect(status().isForbidden());
	}

	// --- Apoio ---

	private long ler() throws Exception {
		String json = leitura(UUID.randomUUID()).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.split("\"idLiberacao\":", 2)[1].split("\\D", 2)[0]);
	}

	private ResultActions leitura(UUID chave) throws Exception {
		return mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO_ANA + "\",\"chaveRequisicao\":\"" + chave + "\"}"));
	}

	private ResultActions girar(long liberacao) throws Exception {
		return girar(liberacao, UUID.randomUUID());
	}

	private ResultActions girar(long liberacao, UUID chave) throws Exception {
		return mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"idLiberacao\":" + liberacao + ",\"chaveRequisicao\":\"" + chave + "\"}"));
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private Map<String, Object> registro(long id) {
		return jdbc.queryForMap("SELECT * FROM registro_acesso WHERE id_registro = ?", id);
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

}
