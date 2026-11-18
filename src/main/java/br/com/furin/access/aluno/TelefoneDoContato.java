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

	/**
	 * O telefone gravado no formato da máscara do formulário (DEC-47): {@code (11) 90000-0001}
	 * para celular e {@code (11) 3000-0001} para fixo. Assim o campo da edição já vem dentro
	 * do tamanho máximo. Um valor que não é telefone volta como está.
	 */
	static String paraExibir(String gravado, String ddi) {
		Optional<String> normalizado = normalizar(gravado, ddi);
		if (normalizado.isEmpty()) {
			return gravado;
		}
		String numeros = normalizado.get().replaceAll("\\D", "").substring(ddi.length());
		String ddd = numeros.substring(0, 2);
		String numero = numeros.substring(2);
		int hifen = numero.length() - 4;
		return "(" + ddd + ") " + numero.substring(0, hifen) + "-" + numero.substring(hifen);
	}

}
