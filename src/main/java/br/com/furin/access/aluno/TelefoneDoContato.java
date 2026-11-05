package br.com.furin.access.aluno;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Telefone do contato principal (RF04, dados utilizados): aceita o que se costuma digitar,
 * como {@code (11) 90000-0001} ou {@code +55 11 90000-0001}, e grava sempre com DDI e DDD,
 * como {@code +55 11 900000001}.
 */
final class TelefoneDoContato {

	/** DDD (2) + número fixo (8) ou celular (9). */
	private static final int MIN_DIGITOS = 10;
	private static final int MAX_DIGITOS = 11;

	private static final Pattern CARACTERES_ACEITOS = Pattern.compile("[+()\\d\\s.-]+");

	private TelefoneDoContato() {
	}

	/** @return o telefone normalizado, ou vazio se não for um telefone com DDD */
	static Optional<String> normalizar(String digitado, String ddi) {
		if (digitado == null || !CARACTERES_ACEITOS.matcher(digitado).matches()) {
			return Optional.empty();
		}
		String numeros = digitado.replaceAll("\\D", "");
		int semDdi = numeros.length() - ddi.length();
		if (numeros.startsWith(ddi) && semDdi >= MIN_DIGITOS && semDdi <= MAX_DIGITOS) {
			numeros = numeros.substring(ddi.length());
		}
		if (numeros.length() < MIN_DIGITOS || numeros.length() > MAX_DIGITOS || numeros.startsWith("0")) {
			return Optional.empty();
		}
		return Optional.of("+" + ddi + " " + numeros.substring(0, 2) + " " + numeros.substring(2));
	}

}
