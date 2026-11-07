package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Registro da revogação do termo (T-20; RF05, fluxo A2; CA-RF05-08 e 09; DEC-34). Sem a
 * transação do teste: o que a transação da revogação grava ou desfaz é conferido de
 * verdade. A cena é gravada antes de cada teste e apagada depois.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class RevogacaoDoTermoTests {

	private static final String MATRICULA = "T20-000001";
	private static final String TURMA = "6A-T20";
	private static final String CODIGO = "e1b2c3d4e5f60718293a4b5c6d7e8f94";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RevogacaoDoTermo revogacao;

	@MockitoSpyBean
	private RegistrosDeAlunos registros;

	private long aluno;
	private long vinculo;
	private long consentimento;

	@BeforeEach
	void preparar() {
		apagarACena();
		LocalDateTime antesDeOntem = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', 'revogacao.teste', "
				+ "'{noop}nao-usada', ?, 1, 0, NULL, ?)", SegurancaConfig.PERFIL_FUNCIONARIO, antesDeOntem);
		long usuario = id("SELECT id_usuario FROM usuario WHERE login = 'revogacao.teste'");
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", TURMA);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Ana Teste', ?, 1, ?)",
				MATRICULA, UUID.randomUUID() + ".png", antesDeOntem);
		aluno = id("SELECT id_aluno FROM aluno WHERE matricula = '" + MATRICULA + "'");
		jdbc.update("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (?, "
				+ "(SELECT id_turma FROM turma WHERE nome = ?), ?)", aluno, TURMA, antesDeOntem);
		vinculo = id("SELECT id_vinculo FROM vinculo_turma WHERE id_aluno = " + aluno);
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, 'Carla Teste', '+55 11 900000001', 'carla.teste@example.com', 1, 1, 1)",
				aluno);
		long contato = id("SELECT id_contato FROM contato_responsavel WHERE id_aluno = " + aluno);
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, ativo, principal, recebe_notificacao) "
				+ "VALUES (?, 'Bruno Exemplo', '+55 11 900000002', 1, 0, 1)", aluno);
		jdbc.update("INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, "
				+ "id_usuario_conferente, conferido_em) VALUES (?, 'v1-2026', ?, 'TERMO-SIGILOSO-0001', ?, ?)",
				contato, antesDeOntem.toLocalDate(), usuario, antesDeOntem);
		consentimento = id("SELECT id_consentimento FROM consentimento WHERE id_contato = " + contato);
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (?, ?, ?)", aluno, CODIGO,
				antesDeOntem);
	}

	@AfterEach
	void apagarACena() {
		String doAluno = "(SELECT id_aluno FROM aluno WHERE matricula = '" + MATRICULA + "')";
		jdbc.update("DELETE r FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "WHERE v.id_aluno = " + doAluno);
		jdbc.update("DELETE FROM carteirinha WHERE id_aluno = " + doAluno);
		jdbc.update("DELETE c FROM consentimento c JOIN contato_responsavel r ON r.id_contato = c.id_contato "
				+ "WHERE r.id_aluno = " + doAluno);
		jdbc.update("DELETE FROM contato_responsavel WHERE id_aluno = " + doAluno);
		jdbc.update("DELETE FROM vinculo_turma WHERE id_aluno = " + doAluno);
		jdbc.update("DELETE FROM aluno WHERE matricula = ?", MATRICULA);
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = 'revogacao.teste'");
	}

	// --- CA-RF05-09 ---

	@Test
	void revogacaoInativaOAlunoRevogaACarteirinhaEInativaOsContatos() throws Exception {
		revogar(hoje().toString())
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("revogação registrada")));

		assertThat(jdbc.queryForObject("SELECT revogado_em FROM consentimento WHERE id_consentimento = ?",
				LocalDateTime.class, consentimento)).isCloseTo(LocalDateTime.now(ZoneOffset.UTC),
						org.assertj.core.api.Assertions.within(1, ChronoUnit.MINUTES));
		assertThat(jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno)).isFalse();
		assertThat(jdbc.queryForObject("SELECT revogada_em FROM carteirinha WHERE id_aluno = ?", LocalDateTime.class,
				aluno)).isNotNull();
		// Os dois contatos, não só o principal: nenhum recebe mais aviso (DEC-37)
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contato_responsavel WHERE id_aluno = ? AND ativo = 1",
				Integer.class, aluno)).isZero();
	}

	@Test
	void depoisDaRevogacaoALeituraDaCarteirinhaEhRecusada() throws Exception {
		// CA-RF05-09; RF23
		revogar(hoje().toString()).andExpect(status().isOk());

		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("CARTEIRINHA_REVOGADA"));
	}

	@Test
	void tudoNaMesmaTransacaoUmaFalhaNoFimDesfazOResto() {
		// CA-RF05-09: a falha ao inativar os contatos, o último passo, desfaz os anteriores
		doThrow(new DataAccessResourceFailureException("falha simulada")).when(registros).inativarContatos(anyLong());

		LocalDate hoje = hoje();

		assertThatThrownBy(() -> revogacao.registrar(aluno, consentimento, hoje))
				.isInstanceOf(DataAccessResourceFailureException.class);

		assertThat(jdbc.queryForObject("SELECT revogado_em FROM consentimento WHERE id_consentimento = ?",
				LocalDateTime.class, consentimento)).isNull();
		assertThat(jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno)).isTrue();
		assertThat(jdbc.queryForObject("SELECT revogada_em FROM carteirinha WHERE id_aluno = ?", LocalDateTime.class,
				aluno)).isNull();
	}

	@Test
	void dataDoPassadoGravaOFimDaqueleDia() throws Exception {
		LocalDate ontem = hoje().minusDays(1);

		revogar(ontem.toString()).andExpect(status().isOk());

		LocalDateTime esperado = LocalDateTime.ofInstant(ontem.atTime(LocalTime.MAX)
				.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
		assertThat(jdbc.queryForObject("SELECT revogado_em FROM consentimento WHERE id_consentimento = ?",
				LocalDateTime.class, consentimento)).isEqualTo(esperado);
	}

	// --- CA-RF05-08 ---

	@Test
	void logTemSoOEventoEOIdDoConsentimento(CapturedOutput saida) throws Exception {
		revogar(hoje().toString()).andExpect(status().isOk());

		assertThat(saida.getOut()).contains("evento=CONSENTIMENTO_REVOGADO", "consentimento=" + consentimento)
				.doesNotContain("TERMO-SIGILOSO", "Ana Teste", "Carla Teste", "carla.teste@example.com");
	}

	// --- Recusas: nada muda ---

	@Test
	void dataVaziaFuturaOuAnteriorAConferenciaEhRecusada() throws Exception {
		revogar("").andExpect(content().string(containsString("Informe a data em que o responsável revogou o termo.")));
		revogar(hoje().plusDays(1).toString())
				.andExpect(content().string(containsString("A data da revogação não pode ser posterior a hoje.")));
		revogar(hoje().minusDays(10).toString())
				.andExpect(content().string(containsString("não pode ser anterior à conferência do termo")));
		assertThat(jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno)).isTrue();
	}

	@Test
	void alunoNaEscolaPrecisaDaSaidaAntes() throws Exception {
		// RF05, fluxo A2, passo 4
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, (SELECT id_usuario FROM usuario WHERE login = "
				+ "'revogacao.teste'), (SELECT id_carteirinha FROM carteirinha WHERE codigo = ?), 'ENTRADA', 'LEITURA', "
				+ "'CONFIRMADA', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?)", vinculo, CODIGO, UUID.randomUUID().toString());

		revogar(hoje().toString()).andExpect(content().string(containsString("Registre a saída antes")));

		assertThat(jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno)).isTrue();
	}

	@Test
	void termoJaRevogadoNaoEhRevogadoDeNovo() throws Exception {
		revogar(hoje().toString()).andExpect(status().isOk());

		mvc.perform(get(endereco()).with(funcionario()))
				.andExpect(content().string(containsString("Este termo já foi revogado.")))
				.andExpect(content().string(not(containsString("Registrar revogação</button>"))));
		revogar(hoje().toString()).andExpect(content().string(containsString("Este termo já foi revogado.")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carteirinha WHERE id_aluno = ?", Integer.class, aluno))
				.isEqualTo(1);
	}

	@Test
	void consentimentoDeOutroAlunoDa404() throws Exception {
		mvc.perform(get("/alunos/" + (aluno + 1000) + "/consentimentos/" + consentimento + "/revogacao")
				.with(funcionario())).andExpect(status().isNotFound());
	}

	// --- Acesso ---

	@Test
	void semLoginLevaAoLoginENadaMuda() throws Exception {
		mvc.perform(post(endereco()).param("data", hoje().toString()).with(csrf())).andExpect(redirectedUrl("/login"));
		assertThat(jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno)).isTrue();
	}

	@Test
	void telaTemTituloCampoComLabelEAviso() throws Exception {
		mvc.perform(get(endereco()).with(funcionario()))
				.andExpect(content().string(containsString("<title>Registrar revogação do termo | Furin Access</title>")))
				.andExpect(content().string(containsString("Data em que o responsável revogou o termo (obrigatório)")))
				.andExpect(content().string(containsString("Ana Teste")))
				.andExpect(content().string(not(containsString("TERMO-SIGILOSO"))));
	}

	// --- Apoio ---

	private ResultActions revogar(String data) throws Exception {
		return mvc.perform(post(endereco()).param("data", data).with(funcionario()).with(csrf()));
	}

	private String endereco() {
		return "/alunos/" + aluno + "/consentimentos/" + consentimento + "/revogacao";
	}

	private static LocalDate hoje() {
		return LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA);
	}

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private static RequestPostProcessor funcionario() {
		return user("revogacao.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
