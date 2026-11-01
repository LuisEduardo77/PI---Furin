package br.com.furin.access.auditoria;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras de mascaramento do log (T-10; A10 §8.15; RNF05; RF02; RF23). */
class MascaramentoTests {

	@ParameterizedTest
	@CsvSource({
			"carla.teste@example.com, c***@example.com",
			"Ana.Teste@example.com,   A***@example.com",
			"a@example.com,           a***@example.com",
			"'  bia@example.com  ',   b***@example.com"
	})
	void emailMostraSoAPrimeiraLetraEODominio(String email, String esperado) {
		assertThat(Mascaramento.email(email)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "   ", "sem-arroba", "@example.com", "carla@" })
	void emailInvalidoViraSoAMascara(String email) {
		assertThat(Mascaramento.email(email)).isEqualTo("***");
	}

	@ParameterizedTest
	@CsvSource({
			"ate.demo,                a***",
			"gestao.demo,             g***",
			"carla.teste@example.com, c***@example.com"
	})
	void loginMostraSoAPrimeiraLetra(String login, String esperado) {
		assertThat(Mascaramento.login(login)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@CsvSource({
			"0123456789abcdef0123456789abcdef, 0123***",
			"a1b2c3,                           a1b2***",
			"ab,                               ab***"
	})
	void codigoDaCarteirinhaMostraSoOs4PrimeirosCaracteres(String codigo, String esperado) {
		assertThat(Mascaramento.codigoCarteirinha(codigo)).isEqualTo(esperado);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void codigoVazioViraSoAMascara(String codigo) {
		assertThat(Mascaramento.codigoCarteirinha(codigo)).isEqualTo("***");
	}

}
