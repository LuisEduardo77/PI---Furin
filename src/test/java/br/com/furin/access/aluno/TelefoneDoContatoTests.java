package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Telefone do contato com DDI e DDD (RF04, dados utilizados). */
class TelefoneDoContatoTests {

	private static final String DDI = "55";

	@ParameterizedTest
	@CsvSource({
			"(11) 90000-0001, +55 11 900000001",
			"11 90000-0001, +55 11 900000001",
			"+55 11 90000-0001, +55 11 900000001",
			"5511900000001, +55 11 900000001",
			"(11) 3000-0001, +55 11 30000001",
			// DDD 55 (Rio Grande do Sul): não é confundido com o DDI
			"(55) 90000-0001, +55 55 900000001" })
	void gravaComDdiEDdd(String digitado, String esperado) {
		assertThat(TelefoneDoContato.normalizar(digitado, DDI)).contains(esperado);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "abc", "90000-0001", "(11) 90000-00011", "(01) 90000-0001", "11 90000-0001 ramal 2" })
	void recusaOQueNaoETelefoneComDdd(String digitado) {
		assertThat(TelefoneDoContato.normalizar(digitado, DDI)).isEmpty();
	}

}
