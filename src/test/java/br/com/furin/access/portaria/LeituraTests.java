package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.RelogioConfig;
import br.com.furin.access.config.SegurancaConfig;

/**
 * {@code POST /portaria/leituras} (T-26; RF09, RF13, RF14; RN01, RN02, RN06, RN08,
 * RN09). A janela de 2 s, a RN04/RN05 completas (T-27) e a expiração (T-29) não estão
 * aqui.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class LeituraTests {

	private static final String CODIGO_ANA = "a1b2c3d4e5f60718293a4b5c6d7e8f90";
	private static final String FUNCIONARIO = "portaria.teste";

	/** 20/10/2026, 12:30 no horário da escola. */
	private static final Instant AGORA = LocalDateTime.of(2026, 10, 20, 12, 30)
			.atZone(RelogioConfig.FUSO_DA_ESCOLA).toInstant();

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
	private long ana;
	private long vinculoAna;
	private long carteirinhaAna;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant ontem = agora().minus(Duration.ofDays(1));
		idFuncionario = cena.funcionario(FUNCIONARIO, agora());
		long turma6A = cena.turma("6A-T26");
		ana = cena.aluno("T26-001", "Ana Teste", true, ontem);
		vinculoAna = cena.vinculo(ana, turma6A, ontem.minus(Duration.ofDays(30)), null);
		carteirinhaAna = cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
	}

	// --- Liberação (RF09, RF14; RN02, passo 1) ---

	@Test
	void leituraValidaDevolveALiberacaoDeEntradaComNomeETurma() throws Exception {
		UUID chave = UUID.randomUUID();
		ler(CODIGO_ANA, chave)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("ENTRADA"))
				.andExpect(jsonPath("$.situacao").value("AGUARDANDO_GIRO"))
				.andExpect(jsonPath("$.nome").value("Ana Teste"))
				.andExpect(jsonPath("$.primeiroNome").value("Ana"))
				.andExpect(jsonPath("$.turma").value("6A-T26"))
				.andExpect(jsonPath("$.idLiberacao").isNumber())
				.andExpect(jsonPath("$.ocorridoEm").value(nullValue()));

		Map<String, Object> registro = unicoRegistroDaAna();
		// Os IDs são BIGINT UNSIGNED e voltam do JDBC como BigInteger
		assertThat(idDe(registro, "id_vinculo")).isEqualTo(vinculoAna);
		assertThat(idDe(registro, "id_carteirinha")).isEqualTo(carteirinhaAna);
		assertThat(idDe(registro, "id_usuario")).isEqualTo(idFuncionario);
		assertThat(registro)
				.containsEntry("situacao", "AGUARDANDO_GIRO")
				.containsEntry("forma", "LEITURA")
				.containsEntry("tipo", "ENTRADA")
				.containsEntry("chave_requisicao", chave.toString())
				.containsEntry("lancado_em", CenaDaPortaria.utc(agora()))
				.containsEntry("ocorrido_em", null);
	}

	@Test
	void tipoEnviadoPelaInterfaceEhIgnorado() throws Exception {
		// CA-RF13-07, CA-RF09-06: o aluno está fora da escola, então é ENTRADA
		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO_ANA + "\",\"chaveRequisicao\":\"" + UUID.randomUUID()
						+ "\",\"tipo\":\"SAIDA\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("ENTRADA"));
	}

	@Test
	void depoisDeUmaEntradaConfirmadaHojeOTipoEhSaida() throws Exception {
		// CA-RF14-01: ENTRADA às 07:02 de hoje
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, hojeAs(7, 2));

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.tipo").value("SAIDA"));
	}

	@Test
	void depoisDeUmaSaidaConfirmadaOTipoVoltaASerEntrada() throws Exception {
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, hojeAs(7, 2));
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.SAIDA, hojeAs(11, 40));

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(jsonPath("$.tipo").value("ENTRADA"));
	}

	@Test
	void liberacaoCanceladaNaoContaParaOTipo() throws Exception {
		// CA-RF13-06: a última CONFIRMADA é a ENTRADA de hoje; a SAIDA cancelada não vale
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA, hojeAs(7, 2));
		cena.cancelada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.SAIDA, hojeAs(11, 0));

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(jsonPath("$.tipo").value("SAIDA"));
	}

	@Test
	void codigoEmMaiusculasEComEspacosEhAceito() throws Exception {
		// Leitor ou colagem podem mandar o código em maiúsculas
		ler("  " + CODIGO_ANA.toUpperCase() + " ", UUID.randomUUID()).andExpect(status().isOk());
	}

	// --- Reenvio (RN08; CA-RF09-07) ---

	@Test
	void reenvioComAMesmaChaveDevolveAMesmaLiberacaoSemCriarOutra() throws Exception {
		UUID chave = UUID.randomUUID();
		long primeira = idDaLiberacao(ler(CODIGO_ANA, chave).andExpect(status().isOk()));
		long segunda = idDaLiberacao(ler(CODIGO_ANA, chave).andExpect(status().isOk()));

		assertThat(segunda).isEqualTo(primeira);
		assertThat(quantidadeDeRegistrosDaAna()).isEqualTo(1);
	}

	// --- Recusas (RN09, RN06): nada é gravado ---

	@ParameterizedTest
	@ValueSource(strings = { "nao-e-um-codigo", "a1b2c3d4", "ffffffffffffffffffffffffffffffff" })
	void codigoForaDoFormatoOuDesconhecidoEhRecusado(String codigo) throws Exception {
		ler(codigo, UUID.randomUUID()).andExpect(recusa("CODIGO_NAO_RECONHECIDO"));
		assertThat(quantidadeDeRegistrosDaAna()).isZero();
	}

	@Test
	void carteirinhaRevogadaEhRecusada() throws Exception {
		jdbc.update("UPDATE carteirinha SET revogada_em = emitida_em WHERE id_carteirinha = ?", carteirinhaAna);

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("CARTEIRINHA_REVOGADA"));
		assertThat(quantidadeDeRegistrosDaAna()).isZero();
	}

	@Test
	void carteirinhaExpiradaEhRecusada() throws Exception {
		jdbc.update("UPDATE carteirinha SET expira_em = ? WHERE id_carteirinha = ?",
				CenaDaPortaria.utc(agora().minusSeconds(1)), carteirinhaAna);

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("CARTEIRINHA_EXPIRADA"));
	}

	@Test
	void alunoInativoEhRecusado() throws Exception {
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", ana);

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("ALUNO_INATIVO"));
	}

	@Test
	void alunoSemTurmaVigenteEhRecusado() throws Exception {
		jdbc.update("UPDATE vinculo_turma SET fim_em = ? WHERE id_vinculo = ?",
				CenaDaPortaria.utc(agora().minusSeconds(1)), vinculoAna);

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("SEM_TURMA_VIGENTE"));
	}

	@Test
	void entradaDeOntemSemSaidaRecusaPorSaidaPendente() throws Exception {
		// CA-RF13-04; RN06: o dia da entrada vai na resposta, para "Saída pendente de DD/MM"
		cena.confirmada(vinculoAna, idFuncionario, carteirinhaAna, TipoMovimentacao.ENTRADA,
				hojeAs(7, 2).minus(Duration.ofDays(1)));

		ler(CODIGO_ANA, UUID.randomUUID())
				.andExpect(recusa("SAIDA_PENDENTE"))
				.andExpect(jsonPath("$.saidaPendenteDe").value("2026-10-19"));
		assertThat(quantidadeDeRegistrosDaAna()).isEqualTo(1);
	}

	@Test
	void segundaLeituraComLiberacaoPendenteNaoCriaOutra() throws Exception {
		// Proteção mínima até a T-27 (RN04): nunca duas liberações pendentes para o mesmo aluno
		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(status().isOk());

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("LIBERACAO_PENDENTE"));
		assertThat(quantidadeDeRegistrosDaAna()).isEqualTo(1);
	}

	@Test
	void recusaVaiParaOLogSoComOsQuatroPrimeirosCaracteresDoCodigo(CapturedOutput saida) throws Exception {
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", ana);

		ler(CODIGO_ANA, UUID.randomUUID()).andExpect(recusa("ALUNO_INATIVO"));

		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=LEITURA_RECUSADA")))
				.singleElement().asString()
				.contains("codigo=a1b2***", "motivo=ALUNO_INATIVO", "usuario=p***")
				.doesNotContain(CODIGO_ANA);
		// Nenhum evento de auditoria leva o código inteiro (A10 §8.15; RF23)
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=")))
				.noneMatch(linha -> linha.contains(CODIGO_ANA));
	}

	// --- Entrada inválida e segurança ---

	@Test
	void pedidoSemCodigoOuComChaveInvalidaEhRecusadoPelaValidacao() throws Exception {
		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO_ANA + "\",\"chaveRequisicao\":\"nao-e-uuid\"}"))
				.andExpect(status().isBadRequest());
		assertThat(quantidadeDeRegistrosDaAna()).isZero();
	}

	@Test
	void leituraSemTokenCsrfEhRecusada() throws Exception {
		mvc.perform(post("/portaria/leituras").with(funcionario())
				.contentType(MediaType.APPLICATION_JSON).content(corpo(CODIGO_ANA, UUID.randomUUID())))
				.andExpect(status().isForbidden());
	}

	@Test
	void leituraSemLoginLevaAoLogin() throws Exception {
		mvc.perform(post("/portaria/leituras").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo(CODIGO_ANA, UUID.randomUUID())))
				.andExpect(redirectedUrl("/login"));
	}

	// --- Apoio ---

	private ResultActions ler(String codigo, UUID chave) throws Exception {
		return mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(corpo(codigo, chave)));
	}

	private static String corpo(String codigo, UUID chave) {
		return "{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + chave + "\"}";
	}

	private static ResultMatcher recusa(String motivo) {
		return resultado -> {
			status().isUnprocessableContent().match(resultado);
			jsonPath("$.motivo").value(motivo).match(resultado);
		};
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private long idDaLiberacao(ResultActions resposta) throws Exception {
		String json = resposta.andReturn().getResponse().getContentAsString();
		return Long.parseLong(json.replaceAll(".*\"idLiberacao\":(\\d+).*", "$1"));
	}

	private static long idDe(Map<String, Object> linha, String coluna) {
		return ((Number) linha.get(coluna)).longValue();
	}

	private Map<String, Object> unicoRegistroDaAna() {
		return jdbc.queryForMap("SELECT r.* FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "WHERE v.id_aluno = ?", ana);
	}

	private int quantidadeDeRegistrosDaAna() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso r JOIN vinculo_turma v "
				+ "ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ?", Integer.class, ana);
	}

	private Instant agora() {
		return relogio.instant();
	}

	private Instant hojeAs(int hora, int minuto) {
		return LocalDateTime.of(2026, 10, 20, hora, minuto).atZone(RelogioConfig.FUSO_DA_ESCOLA).toInstant();
	}

}
