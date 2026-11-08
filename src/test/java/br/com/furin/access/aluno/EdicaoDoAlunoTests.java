package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Edição do aluno (T-23; RF08; CA-RF08-01 a 09). Sem a transação do teste: a edição grava de
 * verdade, e a cena é apagada depois de cada teste. A sessão guarda o que foi revisado até a
 * confirmação. Só dados fictícios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class EdicaoDoAlunoTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
	private static final byte[] OUTRO_PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 1 };

	private static final String MATRICULA = "T23-0001";
	private static final String MATRICULA_DE_OUTRO = "T23-0002";
	private static final String CODIGO = "d00dfeed00d00dfeed00d00dfeed0001";

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-edicao-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", AREA_PRIVADA::toString);
	}

	@AfterAll
	static void apagarAAreaPrivada() throws IOException {
		FileSystemUtils.deleteRecursively(AREA_PRIVADA);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private AreaPrivada areaPrivada;

	private long aluno;
	private long outro;
	private long turma6A;
	private long turma6B;
	private long vinculo6A;
	private long contato;
	private String fotoAtual;

	private MockHttpSession sessao;

	@BeforeEach
	void preparar() throws IOException {
		apagarACena();
		LocalDateTime ontem = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', 'edicao.teste', "
				+ "'{noop}nao-usada', ?, 1, 0, NULL, ?)", SegurancaConfig.PERFIL_FUNCIONARIO, ontem);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES ('6A-T23', 2026, 'MANHA', 1)");
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES ('6B-T23', 2026, 'MANHA', 1)");
		turma6A = id("SELECT id_turma FROM turma WHERE nome = '6A-T23'");
		turma6B = id("SELECT id_turma FROM turma WHERE nome = '6B-T23'");
		fotoAtual = areaPrivada.guardar(TipoDeDocumento.FOTO, new ByteArrayInputStream(PNG));
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Bia Exemplo', ?, 1, ?)",
				MATRICULA, fotoAtual, ontem.minusDays(30));
		aluno = id("SELECT id_aluno FROM aluno WHERE matricula = '" + MATRICULA + "'");
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Davi Teste', "
				+ "'sem-arquivo.png', 1, ?)", MATRICULA_DE_OUTRO, ontem);
		outro = id("SELECT id_aluno FROM aluno WHERE matricula = '" + MATRICULA_DE_OUTRO + "'");
		jdbc.update("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (?, ?, ?)", aluno, turma6A,
				ontem.minusDays(30));
		vinculo6A = id("SELECT id_vinculo FROM vinculo_turma WHERE id_aluno = " + aluno);
		// Telefone gravado antes da normalização, como na carga fictícia
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, 'Bruno Exemplo', '(11) 90000-0002', 'bruno.pai@example.com', 1, 1, 1)",
				aluno);
		contato = id("SELECT id_contato FROM contato_responsavel WHERE id_aluno = " + aluno);
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (?, ?, ?)", aluno, CODIGO, ontem);
		sessao = new MockHttpSession();
	}

	@AfterEach
	void apagarACena() {
		LimpezaDeAlunos.apagar(jdbc, "matricula IN (?, ?)", MATRICULA, MATRICULA_DE_OUTRO);
		jdbc.update("DELETE FROM turma WHERE nome IN ('6A-T23', '6B-T23')");
		jdbc.update("DELETE FROM usuario WHERE login = 'edicao.teste'");
	}

	// --- CA-RF08-01: resumo com Corrigir e Confirmar ---

	@Test
	void formularioVemComOsDadosAtuaisEAsOutrasAcoes() throws Exception {
		mvc.perform(get(editar()).with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Editar aluno Bia Exemplo | Furin Access</title>")))
				.andExpect(content().string(containsString("value=\"" + MATRICULA + "\"")))
				.andExpect(content().string(containsString("value=\"bruno.pai@example.com\"")))
				.andExpect(content().string(containsString("Nova foto (opcional)")))
				.andExpect(content().string(containsString("Enviar avisos de entrada e saída por e-mail ao contato principal")))
				.andExpect(content().string(containsString("Emitir segunda via")))
				.andExpect(content().string(containsString("Inativar aluno")))
				// CA-RF08-04: não existe opção de remover a foto
				.andExpect(content().string(not(containsString("Remover foto"))));
	}

	@Test
	void revisarMostraAntesEDepoisSemGravarECorrigirVoltaComOsValores() throws Exception {
		revisar(Map.of("nome", "Bia Maria Exemplo"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Revisar alterações de Bia Exemplo")))
				.andExpect(content().string(containsString("<td>Bia Exemplo</td>")))
				.andExpect(content().string(containsString("<td>Bia Maria Exemplo</td>")))
				.andExpect(content().string(containsString(">Confirmar</button>")))
				.andExpect(content().string(containsString("Corrigir</a>")));
		assertThat(nomeDoAluno()).isEqualTo("Bia Exemplo");

		mvc.perform(get(editar() + "?corrigir=sim").session(sessao).with(funcionario()))
				.andExpect(content().string(containsString("value=\"Bia Maria Exemplo\"")));
		assertThat(nomeDoAluno()).isEqualTo("Bia Exemplo");
	}

	@Test
	void confirmarGravaOQueFoiRevisado() throws Exception {
		revisar(Map.of("nome", "Bia Maria Exemplo"));

		confirmar()
				.andExpect(redirectedUrl(editar()))
				.andExpect(flash().attribute("cadastroAtualizado", true));
		assertThat(nomeDoAluno()).isEqualTo("Bia Maria Exemplo");
	}

	@Test
	void semAlteracaoNadaParaRevisarMesmoComOTelefoneAntigo() throws Exception {
		// "(11) 90000-0002" gravado antes é o mesmo telefone que "+55 11 900000002"
		revisar(Map.of()).andExpect(content().string(containsString("nenhuma alteração para revisar")));
	}

	@Test
	void confirmarSemRevisaoNaoGravaNada() throws Exception {
		confirmar().andExpect(redirectedUrl(editar())).andExpect(flash().attribute("revisaoPerdida", true));
	}

	// --- CA-RF08-02: troca de turma ---

	@Test
	void trocaDeTurmaCriaVinculoNovoEOHistoricoContinuaNaTurmaAntiga() throws Exception {
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, (SELECT id_usuario FROM usuario WHERE login = "
				+ "'edicao.teste'), (SELECT id_carteirinha FROM carteirinha WHERE codigo = ?), 'ENTRADA', 'LEITURA', "
				+ "'CONFIRMADA', ?, ?, ?)", vinculo6A, CODIGO, LocalDateTime.now(ZoneOffset.UTC).minusHours(3),
				LocalDateTime.now(ZoneOffset.UTC).minusHours(3), UUID.randomUUID().toString());
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, (SELECT id_usuario FROM usuario WHERE login = "
				+ "'edicao.teste'), (SELECT id_carteirinha FROM carteirinha WHERE codigo = ?), 'SAIDA', 'LEITURA', "
				+ "'CONFIRMADA', ?, ?, ?)", vinculo6A, CODIGO, LocalDateTime.now(ZoneOffset.UTC).minusHours(2),
				LocalDateTime.now(ZoneOffset.UTC).minusHours(2), UUID.randomUUID().toString());

		revisar(Map.of("idTurma", String.valueOf(turma6B)))
				.andExpect(content().string(containsString("<td>6A-T23 — Manhã</td>")))
				.andExpect(content().string(containsString("<td>6B-T23 — Manhã</td>")));
		confirmar().andExpect(status().is3xxRedirection());

		assertThat(jdbc.queryForObject("SELECT fim_em FROM vinculo_turma WHERE id_vinculo = ?", LocalDateTime.class,
				vinculo6A)).isNotNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculo_turma WHERE id_aluno = ? AND id_turma = ? "
				+ "AND fim_em IS NULL", Integer.class, aluno, turma6B)).isEqualTo(1);
		// As movimentações antigas continuam ligadas à 6A
		assertThat(jdbc.queryForList("SELECT DISTINCT t.nome FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo JOIN turma t ON t.id_turma = v.id_turma "
				+ "WHERE v.id_aluno = ?", String.class, aluno)).containsExactly("6A-T23");
	}

	// --- CA-RF08-03: matrícula de outro aluno ---

	@Test
	void matriculaDeOutroAlunoEhRecusadaComOLinkParaOCadastroDele() throws Exception {
		revisar(Map.of("matricula", MATRICULA_DE_OUTRO))
				.andExpect(content().string(containsString(ConferenciasDoAluno.MATRICULA_REPETIDA)))
				.andExpect(content().string(containsString("href=\"/alunos/" + outro + "/editar\"")))
				.andExpect(content().string(containsString("Abrir cadastro existente")))
				.andExpect(content().string(containsString("aria-describedby=\"matricula-erro\"")));
		// CA-RF06-04: a própria matrícula não acusa duplicidade
		revisar(Map.of("nome", "Bia Maria Exemplo")).andExpect(content().string(not(containsString("já está cadastrada"))));
	}

	// --- CA-RF08-04: foto ---

	@Test
	void semFotoNovaAAtualContinuaEComFotoNovaAAntigaSaiDaAreaPrivada() throws Exception {
		revisar(Map.of("nome", "Bia Maria Exemplo"));
		confirmar();
		assertThat(fotoDoAluno()).isEqualTo(fotoAtual);

		revisar(Map.of(), new MockMultipartFile("foto", "nova.png", "image/png", OUTRO_PNG))
				.andExpect(content().string(containsString("Nova foto enviada")));
		confirmar().andExpect(status().is3xxRedirection());

		String nova = fotoDoAluno();
		assertThat(nova).isNotEqualTo(fotoAtual);
		assertThat(AREA_PRIVADA.resolve(nova)).hasBinaryContent(OUTRO_PNG);
		// DEC-28, D10: a foto antiga sai depois da gravação, por minimização
		assertThat(Files.exists(AREA_PRIVADA.resolve(fotoAtual))).isFalse();
	}

	@Test
	void fotoInvalidaEhRecusada() throws Exception {
		revisar(Map.of(), new MockMultipartFile("foto", "nova.png", "image/png", "%PDF-1.4".getBytes()))
				.andExpect(content().string(containsString("A foto precisa ser JPEG ou PNG, até 2 MB.")));
		assertThat(fotoDoAluno()).isEqualTo(fotoAtual);
	}

	// --- CA-RF08-05: substituir o contato principal ---

	@Test
	void substituirOContatoPrincipalCriaUmRegistroNovo() throws Exception {
		revisar(Map.of("substituirContato", "true", "contatoNome", "Carla Teste",
				"contatoTelefone", "(11) 90000-0001", "contatoEmail", "carla.teste@example.com"))
				.andExpect(content().string(containsString("Carla Teste (novo contato")));
		confirmar().andExpect(status().is3xxRedirection());

		assertThat(jdbc.queryForObject("SELECT principal FROM contato_responsavel WHERE id_contato = ?", Boolean.class,
				contato)).isFalse();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contato_responsavel WHERE id_aluno = ? AND principal = 1 "
				+ "AND ativo = 1", Integer.class, aluno)).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT nome, telefone, recebe_notificacao FROM contato_responsavel "
				+ "WHERE id_aluno = ? AND principal = 1", aluno))
				.containsEntry("nome", "Carla Teste").containsEntry("telefone", "+55 11 900000001")
				.containsEntry("recebe_notificacao", true);
	}

	@Test
	void mudarONomeDoContatoSemSubstituirEhRecusado() throws Exception {
		revisar(Map.of("contatoNome", "Carla Teste"))
				.andExpect(content().string(containsString("O nome do contato só muda com a substituição")));
	}

	// --- CA-RF08-06: aviso por e-mail (DEC-38) ---

	@Test
	void desligarOAvisoPedeConfirmacaoGravaEVaiParaOLogComOAutor(CapturedOutput saida) throws Exception {
		Map<String, String> semAviso = new LinkedHashMap<>(valoresAtuais());
		semAviso.remove("recebeAviso");

		revisarExatamente(semAviso, null)
				.andExpect(content().string(containsString(
						"Os avisos de Bia Exemplo deixarão de ser enviados a Bruno Exemplo. Confirmar?")));
		confirmar().andExpect(status().is3xxRedirection());

		assertThat(jdbc.queryForObject("SELECT recebe_notificacao FROM contato_responsavel WHERE id_contato = ?",
				Boolean.class, contato)).isFalse();
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=PREFERENCIA_AVISO_ALTERADA")))
				.singleElement().asString()
				.contains("usuario=e***", "aluno=" + aluno, "aviso=DESLIGADO")
				.doesNotContain("bruno.pai@example.com");
	}

	// --- CA-RF08-07: ALUNO_EDITADO só com os nomes dos campos ---

	@Test
	void logDaEdicaoTemSoOsNomesDosCampos(CapturedOutput saida) throws Exception {
		revisar(Map.of("nome", "Bia Maria Exemplo", "idTurma", String.valueOf(turma6B), "contatoEmail",
				"bruno.novo@example.com"));
		confirmar().andExpect(status().is3xxRedirection());

		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=ALUNO_EDITADO")))
				.singleElement().asString()
				.contains("aluno=" + aluno, "campos=nome,turma,contatoEmail")
				.doesNotContain("Bia Maria", "bruno.novo@example.com", "bruno.pai@example.com", "6B-T23");
	}

	// --- CA-RF08-08: inativação ---

	@Test
	void inativarComSaidaPendenteEhRecusado() throws Exception {
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, (SELECT id_usuario FROM usuario WHERE login = "
				+ "'edicao.teste'), (SELECT id_carteirinha FROM carteirinha WHERE codigo = ?), 'ENTRADA', 'LEITURA', "
				+ "'CONFIRMADA', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?)", vinculo6A, CODIGO, UUID.randomUUID().toString());

		mvc.perform(post("/alunos/" + aluno + "/inativacao").with(funcionario()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Registre a saída de Bia antes de inativar o cadastro.")));
		assertThat(ativo()).isTrue();
	}

	@Test
	void inativarSemPendenciaRecusaNovasLeiturasEMantemOHistorico() throws Exception {
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, (SELECT id_usuario FROM usuario WHERE login = "
				+ "'edicao.teste'), (SELECT id_carteirinha FROM carteirinha WHERE codigo = ?), 'SAIDA', 'LEITURA', "
				+ "'CONFIRMADA', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), ?)", vinculo6A, CODIGO, UUID.randomUUID().toString());
		mvc.perform(get("/alunos/" + aluno + "/inativacao").with(funcionario()))
				.andExpect(content().string(containsString("Inativar este aluno?")));

		mvc.perform(post("/alunos/" + aluno + "/inativacao").with(funcionario()).with(csrf()))
				.andExpect(redirectedUrl(editar()));

		assertThat(ativo()).isFalse();
		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("ALUNO_INATIVO"));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ?", Integer.class,
				vinculo6A)).isEqualTo(1);
	}

	// --- CA-RF08-09: sem login ---

	@Test
	void semLoginAEdicaoLevaAoLoginENadaMuda() throws Exception {
		mvc.perform(get(editar())).andExpect(redirectedUrl("/login"));
		mvc.perform(multipart(editar() + "/revisar").param("nome", "Outro Nome").with(csrf()))
				.andExpect(redirectedUrl("/login"));
		mvc.perform(post(editar()).with(csrf())).andExpect(redirectedUrl("/login"));
		mvc.perform(post("/alunos/" + aluno + "/inativacao").with(csrf())).andExpect(redirectedUrl("/login"));

		assertThat(nomeDoAluno()).isEqualTo("Bia Exemplo");
		assertThat(ativo()).isTrue();
	}

	@Test
	void alunoQueNaoExisteDa404() throws Exception {
		mvc.perform(get("/alunos/999999999/editar").with(funcionario())).andExpect(status().isNotFound());
	}

	// --- Apoio ---

	private ResultActions revisar(Map<String, String> mudancas) throws Exception {
		return revisar(mudancas, null);
	}

	private ResultActions revisar(Map<String, String> mudancas, MockMultipartFile foto) throws Exception {
		Map<String, String> valores = new LinkedHashMap<>(valoresAtuais());
		valores.putAll(mudancas);
		return revisarExatamente(valores, foto);
	}

	private ResultActions revisarExatamente(Map<String, String> valores, MockMultipartFile foto) throws Exception {
		MockMultipartHttpServletRequestBuilder pedido = multipart(editar() + "/revisar");
		valores.forEach(pedido::param);
		if (foto != null) {
			pedido.file(foto);
		}
		return mvc.perform(pedido.session(sessao).with(funcionario()).with(csrf()));
	}

	private ResultActions confirmar() throws Exception {
		return mvc.perform(post(editar()).session(sessao).with(funcionario()).with(csrf()));
	}

	private Map<String, String> valoresAtuais() {
		Map<String, String> valores = new LinkedHashMap<>();
		valores.put("nome", "Bia Exemplo");
		valores.put("matricula", MATRICULA);
		valores.put("idTurma", String.valueOf(turma6A));
		valores.put("contatoNome", "Bruno Exemplo");
		valores.put("contatoTelefone", "(11) 90000-0002");
		valores.put("contatoEmail", "bruno.pai@example.com");
		valores.put("recebeAviso", "true");
		return valores;
	}

	private String editar() {
		return "/alunos/" + aluno + "/editar";
	}

	private String nomeDoAluno() {
		return jdbc.queryForObject("SELECT nome FROM aluno WHERE id_aluno = ?", String.class, aluno);
	}

	private String fotoDoAluno() {
		return jdbc.queryForObject("SELECT foto_chave FROM aluno WHERE id_aluno = ?", String.class, aluno);
	}

	private boolean ativo() {
		return jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ?", Boolean.class, aluno);
	}

	private long id(String sql) {
		return jdbc.queryForObject(sql, Long.class);
	}

	private static RequestPostProcessor funcionario() {
		return user("edicao.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
