package br.com.furin.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Sobe a aplicação no perfil test com o banco vazio e confere o esquema criado pela
 * migração V1 (T-06, CA-RNF08-04).
 */
@SpringBootTest(properties = "spring.flyway.clean-disabled=false")
@ActiveProfiles("test")
class EsquemaInicialTests {

	/** As 9 tabelas do DDL de arquitetura-e-dados.md §5.5, validado na T-03. */
	private static final List<String> TABELAS = List.of(
			"usuario", "turma", "aluno", "vinculo_turma", "carteirinha",
			"contato_responsavel", "consentimento", "registro_acesso", "notificacao");

	/**
	 * Esvazia o furin_test antes de migrar. Na CI o banco já nasce vazio; localmente ele
	 * guarda o que a execução anterior criou. Vale só para o contexto deste teste.
	 */
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

	@Test
	void criaAsNoveTabelas() {
		List<String> tabelas = jdbc.queryForList("""
				SELECT TABLE_NAME FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
				  AND TABLE_NAME <> 'flyway_schema_history'
				""", String.class);
		assertThat(tabelas).containsExactlyInAnyOrderElementsOf(TABELAS);
	}

	@Test
	void usaInnoDbEUtf8mb4() {
		List<Map<String, Object>> tabelas = jdbc.queryForList("""
				SELECT TABLE_NAME, ENGINE, TABLE_COLLATION FROM information_schema.TABLES
				WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
				  AND TABLE_NAME <> 'flyway_schema_history'
				""");
		assertThat(tabelas).hasSize(TABELAS.size()).allSatisfy(tabela -> {
			assertThat(tabela.get("ENGINE")).isEqualTo("InnoDB");
			assertThat(tabela.get("TABLE_COLLATION")).isEqualTo("utf8mb4_0900_ai_ci");
		});
	}

	/** Ajuda na inspeção do CA-RNF05-01: nenhum dado que o RNF05 proíbe coletar. */
	@Test
	void naoTemColunasDeDadosNaoColetados() {
		List<String> proibidas = jdbc.queryForList("""
				SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS
				WHERE TABLE_SCHEMA = DATABASE()
				  AND COLUMN_NAME REGEXP '(^|_)(cpf|rg)(_|$)|endereco|nascimento|biometria|religiao|saude|facial'
				""", String.class);
		assertThat(proibidas).isEmpty();
	}

	@Test
	void registraAV1ComSucesso() {
		List<Map<String, Object>> migracoes = jdbc.queryForList("""
				SELECT version, description, success FROM flyway_schema_history
				WHERE version IS NOT NULL ORDER BY installed_rank
				""");
		assertThat(migracoes).hasSize(1);
		assertThat(migracoes.getFirst())
				.containsEntry("version", "1")
				.containsEntry("description", "esquema inicial")
				.containsEntry("success", true);
	}

}
