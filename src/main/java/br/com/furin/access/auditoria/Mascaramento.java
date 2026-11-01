package br.com.furin.access.auditoria;

/**
 * Mascaramento dos dados pessoais que podem aparecer no log (A10 §8.15; RNF05).
 */
public final class Mascaramento {

	static final String MASCARA = "***";

	/** RF23; DEC-28 (A12): do código da carteirinha, só os 4 primeiros caracteres. */
	static final int INICIO_VISIVEL_DO_CODIGO = 4;

	private Mascaramento() {
	}

	/** {@code carla.teste@example.com} → {@code c***@example.com}. Sem "@", só a máscara. */
	public static String email(String email) {
		if (email == null || email.isBlank()) {
			return MASCARA;
		}
		String limpo = email.strip();
		int arroba = limpo.lastIndexOf('@');
		if (arroba <= 0 || arroba == limpo.length() - 1) {
			return MASCARA;
		}
		return limpo.charAt(0) + MASCARA + limpo.substring(arroba);
	}

	/**
	 * Login de funcionário: se for e-mail, mascara como e-mail; senão, só a primeira
	 * letra ({@code ate.demo} → {@code a***}). [PROP]
	 */
	public static String login(String login) {
		if (login == null || login.isBlank()) {
			return MASCARA;
		}
		String limpo = login.strip();
		return limpo.indexOf('@') >= 0 ? email(limpo) : limpo.charAt(0) + MASCARA;
	}

	/** {@code a1b2c3d4...} → {@code a1b2***}. Um código mais curto também ganha a máscara. */
	public static String codigoCarteirinha(String codigo) {
		if (codigo == null || codigo.isBlank()) {
			return MASCARA;
		}
		String limpo = codigo.strip();
		return limpo.substring(0, Math.min(INICIO_VISIVEL_DO_CODIGO, limpo.length())) + MASCARA;
	}

}
