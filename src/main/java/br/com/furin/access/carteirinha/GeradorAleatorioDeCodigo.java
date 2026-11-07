package br.com.furin.access.carteirinha;

import java.security.SecureRandom;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;

/**
 * RN15; A14 p.6: {@code furin.carteirinha.tamanho-codigo} caracteres hexadecimais vindos de
 * {@link SecureRandom}, sem nenhum dado pessoal. Com 32 caracteres, são 128 bits aleatórios.
 */
@Component
public class GeradorAleatorioDeCodigo implements GeradorDeCodigo {

	private final SecureRandom aleatorio = new SecureRandom();

	private final int bytes;

	public GeradorAleatorioDeCodigo(FurinProperties propriedades) {
		// Cada byte vira 2 caracteres hexadecimais
		this.bytes = propriedades.carteirinha().tamanhoCodigo() / 2;
	}

	@Override
	public String gerar() {
		byte[] codigo = new byte[bytes];
		aleatorio.nextBytes(codigo);
		return HexFormat.of().formatHex(codigo);
	}

}
