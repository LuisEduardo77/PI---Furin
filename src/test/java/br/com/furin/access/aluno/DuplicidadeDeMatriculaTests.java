package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.aluno.CadastroDeAlunos.TermoConferido;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Matrícula repetida (T-21; RF06; CA-RF06-01 a 05 e 08). Sem a transação do teste: os
 * cadastros concorrentes precisam de transações de verdade. Matrículas com o prefixo
 * {@code 21} e o sufixo da cena, apagadas no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DuplicidadeDeMatriculaTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };

	private static final String FUNCIONARIO = "duplicidade.teste";
	private static final String TURMA = "6A-T21";
	/** Já usada por um aluno ativo, com zeros à esquerda. */
	private static final String MATRICULA_EM_USO = "0021123";
	private static final String MATRICULA_DE_INATIVO = "0021456";
	private static final String MATRICULA_NOVA = "0021789";
	private static final List<String> MATRICULAS = List.of(MATRICULA_EM_USO, MATRICULA_DE_INATIVO, MATRICULA_NOVA,
			"21123");
	private static final String MENSAGEM = "Esta matrícula já está cadastrada. Confira a matrícula ou localize o "
			+ "cadastro existente para editá-lo.";

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-duplicidade-teste-" + UUID.randomUUID());

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

	@Autowired
	private TransactionTemplate transacao;

	@MockitoSpyBean
	private RegistrosDeAlunos registros;

	private long idTurma;

	private long alunoEmUso;

	@BeforeEach
	void preparar() {
		apagarACena();
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', ?, '{noop}nao-usada', ?, 1, 0, "
				+ "NULL, UTC_TIMESTAMP(6))", FUNCIONARIO, SegurancaConfig.PERFIL_FUNCIONARIO);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", TURMA);
		idTurma = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = ?", Long.class, TURMA);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Ana Teste', "
				+ "'sem-arquivo.png', 1, UTC_TIMESTAMP(6))", MATRICULA_EM_USO);
		alunoEmUso = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class, MATRICULA_EM_USO);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Bia Exemplo', "
				+ "'sem-arquivo.png', 0, UTC_TIMESTAMP(6))", MATRICULA_DE_INATIVO);
	}

	@AfterEach
	void apagarACena() {
		for (String matricula : MATRICULAS) {
			LimpezaDeAlunos.apagar(jdbc, "matricula = ?", matricula);
		}
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	@Test
	void matriculaJaUsadaEhRecusadaComAMensagemDeA11() throws Exception {
		// CA-RF06-01: com o link para o cadastro existente (DEC-45, desde a T-23)
		enviar(MATRICULA_EM_USO)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(MENSAGEM)))
				.andExpect(content().string(containsString("href=\"/alunos/" + alunoEmUso + "/editar\"")))
				.andExpect(content().string(containsString("Abrir cadastro existente")));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE nome = 'Davi Teste'", Integer.class)).isZero();
	}

	@Test
	void matriculaDeAlunoInativoTambemBloqueia() throws Exception {
		// CA-RF06-02: não há reutilização
		enviar(MATRICULA_DE_INATIVO).andExpect(content().string(containsString(MENSAGEM)));
	}

	@Test
	void espacosNasPontasSaoIgnoradosMasOsZerosAEsquerdaContam() throws Exception {
		// CA-RF06-03
		enviar("  " + MATRICULA_EM_USO + "  ").andExpect(content().string(containsString(MENSAGEM)));
		// "21123" é diferente de "0021123": o cadastro é salvo
		enviar("21123").andExpect(status().is3xxRedirection());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = '21123'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void naEdicaoOProprioAlunoFicaDeFora() {
		// CA-RF06-04: a busca que a edição do aluno (T-23) vai usar
		assertThat(registros.alunoComMatricula(MATRICULA_EM_USO, alunoEmUso)).isEmpty();
		assertThat(registros.alunoComMatricula(MATRICULA_EM_USO, null)).contains(alunoEmUso);
		long outro = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class,
				MATRICULA_DE_INATIVO);
		assertThat(registros.alunoComMatricula(MATRICULA_EM_USO, outro)).contains(alunoEmUso);
	}

	@Test
	void duasGravacoesSimultaneasResultamEmUmCadastroSo() throws Exception {
		// CA-RF06-05: a UNIQUE barra a segunda, e o serviço traduz para matrícula repetida.
		// Aqui as transações confirmam de verdade, como fará o cadastro completo (T-22)
		ExecutorService threads = Executors.newFixedThreadPool(2);
		CountDownLatch largada = new CountDownLatch(1);
		List<Future<String>> resultados = new ArrayList<>();
		try {
			for (int i = 0; i < 2; i++) {
				resultados.add(threads.submit(() -> {
					largada.await();
					try {
						transacao.executeWithoutResult(status -> cadastro.gravarBlocos(novo(MATRICULA_NOVA), termo(),
								"sem-arquivo.png", "TERMO-0001", FUNCIONARIO));
						return "SALVO";
					} catch (MatriculaJaCadastrada repetida) {
						return "REPETIDA";
					}
				}));
			}
			largada.countDown();
			List<String> fim = new ArrayList<>();
			for (Future<String> resultado : resultados) {
				fim.add(resultado.get(30, TimeUnit.SECONDS));
			}
			assertThat(fim).containsExactlyInAnyOrder("SALVO", "REPETIDA");
		} finally {
			threads.shutdownNow();
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = ?", Integer.class,
				MATRICULA_NOVA)).isEqualTo(1);
	}

	@Test
	void corridaQuePassaPelaConferenciaRecebeAMensagemENaoErro500() throws Exception {
		// CA-RF06-05: outra requisição gravou a matrícula entre a conferência e a gravação.
		// A conferência é enganada de propósito, e a UNIQUE do banco é quem barra
		doReturn(Optional.empty()).when(registros).alunoComMatricula(anyString(), any());

		enviar(MATRICULA_EM_USO)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString(MENSAGEM)));
	}

	@Test
	void campoComErroTemAriaEOsOutrosContinuamPreenchidos() throws Exception {
		// CA-RF06-08
		enviar(MATRICULA_EM_USO)
				.andExpect(content().string(containsString("id=\"matricula-erro\"")))
				.andExpect(content().string(containsString("aria-describedby=\"matricula-erro\"")))
				.andExpect(content().string(containsString("aria-invalid=\"true\"")))
				.andExpect(content().string(containsString("value=\"" + MATRICULA_EM_USO + "\"")))
				.andExpect(content().string(containsString("value=\"Carla Teste\"")))
				.andExpect(content().string(containsString("value=\"carla.teste@example.com\"")))
				.andExpect(content().string(containsString("value=\"TERMO-0001\"")));
	}

	// --- Apoio ---

	private ResultActions enviar(String matricula) throws Exception {
		MockMultipartHttpServletRequestBuilder pedido = multipart("/alunos");
		Map.ofEntries(Map.entry("nome", "Davi Teste"),
				Map.entry("matricula", matricula),
				Map.entry("idTurma", String.valueOf(idTurma)),
				Map.entry("contatoNome", "Carla Teste"),
				Map.entry("contatoTelefone", "(11) 90000-0001"),
				Map.entry("contatoEmail", "carla.teste@example.com"),
				Map.entry("termoSignatario", "principal"),
				Map.entry("termoVersao", "v1-2026"),
				Map.entry("termoAssinadoEm", LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA).minusDays(1).toString()),
				Map.entry("termoProtocolo", "TERMO-0001"),
				Map.entry("termoConferido", "true")).forEach(pedido::param);
		pedido.file(new MockMultipartFile("foto", "davi.png", "image/png", PNG));
		return mvc.perform(pedido.with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO)).with(csrf()));
	}

	private NovoAluno novo(String matricula) {
		return new NovoAluno("Davi Teste", matricula, idTurma, "Carla Teste", "+55 11 900000001",
				"carla.teste@example.com");
	}

	private static TermoConferido termo() {
		return new TermoConferido("v1-2026", LocalDate.now().minusDays(1), "TERMO-0001");
	}

}
