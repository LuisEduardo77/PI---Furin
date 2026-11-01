package br.com.furin.access.carga;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Carga fictícia do protótipo (T-11; arquitetura §5.6), aplicada pelo Flyway num
 * furin_test vazio. As senhas são descartáveis e existem só neste teste (CA-RNF03-05).
 * No fim, o banco volta a ter só a V1, como os outros testes esperam.
 */
@SpringBootTest(properties = {
		"spring.flyway.clean-disabled=false",
		V2__CargaFicticia.VARIAVEL_SENHA_ATE + "=" + CargaFicticiaTests.SENHA_ATE,
		V2__CargaFicticia.VARIAVEL_SENHA_GESTAO + "=" + CargaFicticiaTests.SENHA_GESTAO })
@ActiveProfiles({ "test", "prototipo" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class CargaFicticiaTests {

	static final String SENHA_ATE = "descartavel-ate-T11";
	static final String SENHA_GESTAO = "descartavel-gestao-T11";

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-carga-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", AREA_PRIVADA::toString);
	}

	/** Começa sempre com o furin_test vazio. */
	@TestConfiguration
	static class BancoVazio {

		@Bean
		FlywayMigrationStrategy limparEMigrar() {
			return flyway -> {
				flyway.clean();
				flyway.migrate();
			};
		}

	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private DataSource dataSource;

	@AfterAll
	void voltarSoParaAV1() throws IOException {
		Flyway soV1 = Flyway.configure().dataSource(dataSource).cleanDisabled(false).load();
		soV1.clean();
		soV1.migrate();
		try (var arquivos = Files.walk(AREA_PRIVADA)) {
			arquivos.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
	}

	@Test
	void flywayRegistraACargaComoV2() {
		List<Map<String, Object>> migracoes = jdbc.queryForList(
				"SELECT version, success FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank");
		assertThat(migracoes).extracting(m -> m.get("version")).containsExactly("1", "2");
		assertThat(migracoes).allSatisfy(m -> assertThat(m).containsEntry("success", true));
	}

	@Test
	void contasDeDemonstracaoComSenhaSoComoHash() {
		List<Map<String, Object>> contas = jdbc.queryForList(
				"SELECT login, nome, senha_hash, perfil, ativo, troca_senha_obrigatoria FROM usuario ORDER BY login");
		assertThat(contas).extracting(c -> c.get("login")).containsExactly("ate.demo", "gestao.demo");
		assertThat(contas).extracting(c -> c.get("nome")).containsExactly("ATE Demo", "Gestão Demo");
		assertThat(contas).allSatisfy(conta -> {
			assertThat(conta).containsEntry("perfil", "FUNCIONARIO").containsEntry("ativo", true);
			assertThat((String) conta.get("senha_hash")).startsWith("{bcrypt}$2")
					.doesNotContain(SENHA_ATE, SENHA_GESTAO);
		});
		assertThat(encoder.matches(SENHA_ATE, (String) contas.get(0).get("senha_hash"))).isTrue();
		assertThat(encoder.matches(SENHA_GESTAO, (String) contas.get(1).get("senha_hash"))).isTrue();
	}

	@Test
	void turmasEAlunosComVinculo() {
		assertThat(jdbc.queryForList("SELECT CONCAT(nome, ' ', turno, ' ', ano_letivo) FROM turma ORDER BY nome",
				String.class)).containsExactly("3B MANHA 2026", "6A MANHA 2026");
		assertThat(jdbc.queryForList("""
				SELECT CONCAT(a.nome, ' ', a.matricula, ' ', t.nome) FROM aluno a
				JOIN vinculo_turma v ON v.id_aluno = a.id_aluno AND v.fim_em IS NULL
				JOIN turma t ON t.id_turma = v.id_turma ORDER BY a.matricula
				""", String.class))
				.containsExactly("Ana Teste 000123 6A", "Davi Teste 000124 3B", "Bia Exemplo 000200 6A");
	}

	@Test
	void contatosSemContaComAvisoLigadoEDesligado() {
		assertThat(jdbc.queryForList("""
				SELECT CONCAT(a.nome, ': ', c.nome, ' ', c.email, ' aviso=', c.recebe_notificacao)
				FROM contato_responsavel c JOIN aluno a ON a.id_aluno = c.id_aluno
				WHERE c.principal = 1 AND c.ativo = 1 ORDER BY a.matricula
				""", String.class)).containsExactly(
				"Ana Teste: Carla Teste carla.teste@example.com aviso=1",
				"Davi Teste: Carla Teste carla.teste@example.com aviso=1",
				"Bia Exemplo: Bruno Exemplo bruno.pai@example.com aviso=0");
		// DEC-37: os responsáveis não têm conta
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario", Integer.class)).isEqualTo(2);
	}

	@Test
	void fotosFicticiasNaAreaPrivada() throws IOException {
		List<String> fotos = jdbc.queryForList("SELECT foto_chave FROM aluno", String.class);
		assertThat(fotos).hasSize(3).doesNotHaveDuplicates()
				.allSatisfy(chave -> assertThat(chave).matches("[0-9a-f-]{36}\\.png"));
		for (String chave : fotos) {
			byte[] png = Files.readAllBytes(AREA_PRIVADA.resolve(chave));
			assertThat(ImageIO.read(new ByteArrayInputStream(png))).isNotNull();
		}
	}

	@Test
	void consentimentosV12026ComTermoEmPdfEProtocolos() throws IOException {
		List<String> referencias = jdbc.queryForList("""
				SELECT c.referencia_termo FROM consentimento c
				JOIN contato_responsavel r ON r.id_contato = c.id_contato
				JOIN aluno a ON a.id_aluno = r.id_aluno
				WHERE c.versao_termo = 'v1-2026' AND c.revogado_em IS NULL ORDER BY a.matricula
				""", String.class);
		assertThat(referencias).hasSize(3);
		assertThat(referencias.get(0)).endsWith(".pdf");
		assertThat(referencias.subList(1, 3)).containsExactly("Protocolo fictício 0002", "Protocolo fictício 0003");
		assertThat(Files.readString(AREA_PRIVADA.resolve(referencias.get(0)), java.nio.charset.StandardCharsets.US_ASCII))
				.startsWith("%PDF-1.4").contains("EXEMPLO FICTICIO");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM consentimento c JOIN usuario u ON u.id_usuario = c.id_usuario_conferente
				WHERE u.login = 'ate.demo'
				""", Integer.class)).isEqualTo(3);
	}

	@Test
	void umaCarteirinhaValidaPorAlunoComCodigoAleatorio() {
		List<String> codigos = jdbc.queryForList(
				"SELECT codigo FROM carteirinha WHERE revogada_em IS NULL", String.class);
		assertThat(codigos).hasSize(3).doesNotHaveDuplicates()
				.allSatisfy(codigo -> assertThat(codigo).matches("[0-9a-f]{32}"));
		assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT id_aluno) FROM carteirinha", Integer.class)).isEqualTo(3);
	}

	@Test
	void nenhumaSenhaNoLog(CapturedOutput saida) {
		// A carga roda na subida do contexto; o log capturado inclui o que foi gravado até aqui
		assertThat(saida.getAll()).doesNotContain(SENHA_ATE, SENHA_GESTAO);
	}

}
