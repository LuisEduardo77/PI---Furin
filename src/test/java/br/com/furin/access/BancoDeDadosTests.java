package br.com.furin.access;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Confere o banco usado pelos testes, localmente e na CI (CA-RNF08-03, T-05).
 */
@SpringBootTest
@ActiveProfiles("test")
class BancoDeDadosTests {

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void usaMySql84() {
		String versao = jdbc.queryForObject("SELECT VERSION()", String.class);
		assertThat(versao).startsWith("8.4.");
	}

	@Test
	void usaOBancoDeTeste() {
		String banco = jdbc.queryForObject("SELECT DATABASE()", String.class);
		assertThat(banco).isEqualTo("furin_test");
	}

}
