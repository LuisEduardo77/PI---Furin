package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.aluno.CadastroDeAlunos.CadastroIncompleto;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Cadastro de aluno (T-19; RF04; RN16). Até a T-20 e a T-22, o cadastro nunca é salvo: o
 * RF04 exige todos os blocos. Sem a transação do teste, para o que a transação do cadastro
 * desfaz ser conferido de verdade. Só dados fictícios (A10 §8.33).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroDeAlunoTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
	private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

	private static final String TURMA = "6A-T19";
	private static final String TURMA_INATIVA = "9Z-T19";
	private static final String MATRICULA_NOVA = "T19-000123";
	private static final String MATRICULA_EXISTENTE = "T19-000999";

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-alunos-teste-" + UUID.randomUUID());

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
	private CadastroDeAlunos cadastro;

	private long idTurma;

	private long idTurmaInativa;

	@BeforeEach
	void preparar() {
		apagarACena();
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", TURMA);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'TARDE', 0)", TURMA_INATIVA);
		idTurma = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = ?", Long.class, TURMA);
		idTurmaInativa = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = ?", Long.class, TURMA_INATIVA);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Bia Exemplo', "
				+ "'sem-arquivo.png', 1, UTC_TIMESTAMP(6))", MATRICULA_EXISTENTE);
	}

	@AfterEach
	void apagarACena() {
		jdbc.update("DELETE FROM aluno WHERE matricula LIKE 'T19-%'");
		jdbc.update("DELETE FROM turma WHERE nome IN (?, ?)", TURMA, TURMA_INATIVA);
	}

	// --- Formulário ---

	@Test
	void formularioTemOsCamposDoRf04ESoAsTurmasAtivas() throws Exception {
		mvc.perform(get("/alunos/novo").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Novo aluno | Furin Access</title>")))
				.andExpect(content().string(containsString("Nome do aluno (obrigatório)")))
				.andExpect(content().string(containsString("Matrícula (obrigatório)")))
				.andExpect(content().string(containsString("Turma (obrigatório)")))
				.andExpect(content().string(containsString("Foto do aluno (obrigatório)")))
				.andExpect(content().string(containsString("Nome do responsável (obrigatório)")))
				.andExpect(content().string(containsString("Telefone com DDD (obrigatório)")))
				.andExpect(content().string(containsString("E-mail (obrigatório)")))
				.andExpect(content().string(containsString("JPEG ou PNG, até 2 MB")))
				.andExpect(content().string(containsString(TURMA + " — Manhã")))
				.andExpect(content().string(not(containsString(TURMA_INATIVA))))
				// A tela avisa que nada é salvo por enquanto
				.andExpect(content().string(containsString("nesta versão o aluno ainda não é salvo")));
	}

	@Test
	void formularioNaoTemCamposDeDadosNaoColetados() throws Exception {
		// CA-RF04-07; CA-RNF05-02
		String pagina = mvc.perform(get("/alunos/novo").with(funcionario()))
				.andReturn().getResponse().getContentAsString().toLowerCase();

		assertThat(pagina).doesNotContain("cpf", "endereço", "nascimento", "biometria", "religião", "saúde");
	}

	@Test
	void inicioDeAlunosLevaAoNovoAluno() throws Exception {
		mvc.perform(get("/alunos").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/alunos/novo\"")));
	}

	// --- RN16: nada é salvo sem todos os blocos ---

	@Test
	void dadosValidosNaoSaoSalvosEATelaDizIsso() throws Exception {
		enviar(valido().file(foto(PNG)))
				.andExpect(status().isOk())
				.andExpect(view().name("alunos/novo"))
				.andExpect(content().string(containsString("Dados conferidos")))
				.andExpect(content().string(containsString("o aluno não")))
				// Os dados continuam na tela
				.andExpect(content().string(containsString("value=\"Ana Teste\"")));

		assertThat(alunosNovos()).isZero();
		assertThat(contatos()).isZero();
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void transacaoRodaEDesfazOsBlocosJaGravados() throws IOException {
		// CA-RF04-02: o aluno e o vínculo foram gravados, e o contato falha (e-mail grande
		// demais para a coluna). Nada fica no banco nem na área privada
		NovoAluno comContatoInvalido = new NovoAluno("Ana Teste", MATRICULA_NOVA, idTurma, "Carla Teste",
				"+55 11 900000001", "a".repeat(300) + "@example.com");
		ByteArrayInputStream foto = new ByteArrayInputStream(PNG);

		assertThatThrownBy(() -> cadastro.cadastrar(comContatoInvalido, foto))
				.isInstanceOf(DataAccessException.class);

		assertThat(alunosNovos()).isZero();
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void comTudoValidoATransacaoTerminaFaltandoOTermoEACarteirinha() throws IOException {
		NovoAluno novo = new NovoAluno("Ana Teste", MATRICULA_NOVA, idTurma, "Carla Teste", "+55 11 900000001",
				"carla.teste@example.com");
		ByteArrayInputStream foto = new ByteArrayInputStream(PNG);

		assertThatThrownBy(() -> cadastro.cadastrar(novo, foto))
				.isInstanceOf(CadastroIncompleto.class);

		assertThat(alunosNovos()).isZero();
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	// --- Erros (CA-RF04-03, 04 e 09) ---

	@ParameterizedTest
	@CsvSource({
			"nome, Informe o nome do aluno.",
			"matricula, Informe a matrícula do aluno.",
			"idTurma, Selecione uma turma ativa.",
			"contatoNome, Informe o nome do responsável.",
			"contatoTelefone, 'Informe o telefone com DDD. Exemplo: (11) 90000-0000.'",
			"contatoEmail, 'Informe um e-mail válido. Exemplo: nome@example.com.'" })
	void campoVazioMostraOErroEPreservaOsOutros(String campo, String mensagem) throws Exception {
		String pagina = enviar(comValor(campo, "   ").file(foto(PNG)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(pagina).contains(mensagem)
				.contains("id=\"" + campo + "-erro\"")
				.contains("aria-invalid=\"true\"")
				.contains("aria-describedby=\"" + campo + "-erro")
				// O resumo no topo leva ao campo
				.contains("id=\"resumo-erros\"", "href=\"#" + campo + "\"");
		if (!"nome".equals(campo)) {
			assertThat(pagina).contains("value=\"Ana Teste\"");
		}
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void semFotoORegistroEhRecusado() throws Exception {
		// CA-RF04-03; DEC-05
		enviar(valido())
				.andExpect(content().string(containsString("Envie a foto do aluno.")))
				.andExpect(content().string(containsString("aria-describedby=\"foto-erro foto-dica\"")))
				.andExpect(content().string(containsString("value=\"" + MATRICULA_NOVA + "\"")));
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void fotoForaDaRegraEhRecusadaPeloConteudo() throws Exception {
		// Um PDF com nome de imagem: o que vale é o conteúdo
		enviar(valido().file(new MockMultipartFile("foto", "ana.png", "image/png", PDF)))
				.andExpect(content().string(containsString("A foto precisa ser JPEG ou PNG, até 2 MB.")));
		enviar(valido().file(foto(Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1))))
				.andExpect(content().string(containsString("A foto precisa ser JPEG ou PNG, até 2 MB.")));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void todosOsErrosAparecemDeUmaVez() throws Exception {
		mvc.perform(multipart("/alunos").with(funcionario()).with(csrf()))
				.andExpect(content().string(containsString("Informe o nome do aluno.")))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")))
				.andExpect(content().string(containsString("Envie a foto do aluno.")))
				.andExpect(content().string(containsString("Informe o nome do responsável.")));
	}

	@Test
	void matriculaJaCadastradaEhRecusadaComAOrientacao() throws Exception {
		// A11 §5.9.9, §5.9.10. Zeros à esquerda fazem parte da matrícula
		enviar(comValor("matricula", MATRICULA_EXISTENTE).file(foto(PNG)))
				.andExpect(content().string(containsString("Esta matrícula já está cadastrada.")))
				.andExpect(content().string(containsString("localize o cadastro existente")));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void turmaInativaEhRecusada() throws Exception {
		enviar(comValor("idTurma", String.valueOf(idTurmaInativa)).file(foto(PNG)))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")));
		enviar(comValor("idTurma", "nao-e-numero").file(foto(PNG)))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")));
	}

	@Test
	void telefoneEEmailInvalidosSaoRecusados() throws Exception {
		// CA-RF04-04; DEC-09
		enviar(comValor("contatoTelefone", "90000-0000").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoTelefone-erro\"")));
		enviar(comValor("contatoEmail", "carla.teste").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoEmail-erro\"")));
		enviar(comValor("contatoEmail", "carla@escola").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoEmail-erro\"")));
	}

	@Test
	void textoComHtmlAparececomoTexto() throws Exception {
		// CA-RF04-11; A10 §8.28
		enviar(comValor("nome", "<b>Ana</b>").file(foto(PNG)))
				.andExpect(content().string(containsString("&lt;b&gt;Ana&lt;/b&gt;")))
				.andExpect(content().string(not(containsString("<b>Ana</b>"))));
	}

	// --- Acesso (CA-RF04-10) ---

	@Test
	void postSemLoginNaoGravaELevaAoLogin() throws Exception {
		mvc.perform(preenchido(multipart("/alunos")).file(foto(PNG)).with(csrf()))
				.andExpect(redirectedUrl("/login"));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void postSemTokenCsrfEhRecusado() throws Exception {
		mvc.perform(preenchido(multipart("/alunos")).file(foto(PNG)).with(funcionario()))
				.andExpect(status().isForbidden());
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	// --- Apoio ---

	private MockMultipartHttpServletRequestBuilder valido() {
		return preenchido(multipart("/alunos"));
	}

	/** Todos os campos válidos, menos um. */
	private MockMultipartHttpServletRequestBuilder comValor(String campo, String valor) {
		MockMultipartHttpServletRequestBuilder pedido = multipart("/alunos");
		camposValidos().forEach((nome, valido) -> pedido.param(nome, nome.equals(campo) ? valor : valido));
		return pedido;
	}

	private MockMultipartHttpServletRequestBuilder preenchido(MockMultipartHttpServletRequestBuilder pedido) {
		camposValidos().forEach(pedido::param);
		return pedido;
	}

	private Map<String, String> camposValidos() {
		return Map.of("nome", "Ana Teste",
				"matricula", MATRICULA_NOVA,
				"idTurma", String.valueOf(idTurma),
				"contatoNome", "Carla Teste",
				"contatoTelefone", "(11) 90000-0001",
				"contatoEmail", "carla.teste@example.com");
	}

	private ResultActions enviar(MockMultipartHttpServletRequestBuilder pedido) throws Exception {
		return mvc.perform(pedido.with(funcionario()).with(csrf()));
	}

	private static MockMultipartFile foto(byte[] conteudo) {
		return new MockMultipartFile("foto", "ana.png", "image/png", conteudo);
	}

	private static RequestPostProcessor funcionario() {
		return user("cadastro.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private int alunosNovos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = ?", Integer.class, MATRICULA_NOVA);
	}

	private int contatos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM contato_responsavel WHERE email = 'carla.teste@example.com'",
				Integer.class);
	}

	private long arquivosNaAreaPrivada() throws IOException {
		if (!Files.exists(AREA_PRIVADA)) {
			return 0;
		}
		try (var arquivos = Files.list(AREA_PRIVADA)) {
			return arquivos.count();
		}
	}

}
