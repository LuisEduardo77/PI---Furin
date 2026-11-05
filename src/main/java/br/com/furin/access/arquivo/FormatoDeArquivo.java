package br.com.furin.access.arquivo;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/**
 * Os formatos da área privada, reconhecidos pelos primeiros bytes do arquivo (DEC-28,
 * D1 e D2). O nome e a extensão que vêm com o arquivo nunca decidem o formato.
 */
enum FormatoDeArquivo {

	JPEG("image/jpeg", ".jpg", new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }),
	PNG("image/png", ".png", new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' }),
	PDF("application/pdf", ".pdf", "%PDF-".getBytes(StandardCharsets.US_ASCII));

	/** Quantos bytes bastam para reconhecer qualquer formato. */
	static final int BYTES_DA_ASSINATURA = 8;

	private final String mime;

	private final String extensao;

	private final byte[] assinatura;

	FormatoDeArquivo(String mime, String extensao, byte[] assinatura) {
		this.mime = mime;
		this.extensao = extensao;
		this.assinatura = assinatura;
	}

	String mime() {
		return mime;
	}

	String extensao() {
		return extensao;
	}

	static Optional<FormatoDeArquivo> doConteudo(byte[] conteudo) {
		return Arrays.stream(values()).filter(formato -> formato.comecaCom(conteudo)).findFirst();
	}

	private boolean comecaCom(byte[] conteudo) {
		return conteudo.length >= assinatura.length
				&& Arrays.equals(conteudo, 0, assinatura.length, assinatura, 0, assinatura.length);
	}

}
