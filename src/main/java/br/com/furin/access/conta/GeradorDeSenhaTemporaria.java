package br.com.furin.access.conta;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;

/**
 * Senha temporária das contas da equipe (T-43; RN14; DEC-28, E4): {@code SecureRandom},
 * com {@code furin.senha.tamanho-temporaria} caracteres.
 * <p>
 * O alfabeto deixa de fora os caracteres que se confundem ao ler ou ditar a senha (0 e O,
 * 1, l e I) [PROP]. Com 56 símbolos e 12 caracteres, são cerca de 69 bits de entropia.
 */
@Component
public class GeradorDeSenhaTemporaria {

	static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

	private final SecureRandom aleatorio = new SecureRandom();

	private final int tamanho;

	public GeradorDeSenhaTemporaria(FurinProperties propriedades) {
		this.tamanho = propriedades.senha().tamanhoTemporaria();
	}

	public String gerar() {
		StringBuilder senha = new StringBuilder(tamanho);
		for (int i = 0; i < tamanho; i++) {
			senha.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
		}
		return senha.toString();
	}

}
