package br.com.furin.access.carteirinha;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Component;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * QR Code da carteirinha (T-22; RF07, impressão, passo 2; DEC-07; DEC-28, D8 e D9): gerado no
 * servidor com ZXing, nível de correção M, como PNG. O conteúdo é <b>somente o código</b>:
 * nem nome, nem matrícula, nem URL (CA-RF07-03).
 */
@Component
public class QrCodeDaCarteirinha {

	/** Pixels de cada lado. A página de impressão fixa o tamanho em milímetros. */
	private static final int LADO = 300;

	/** Zona clara em volta, em módulos (o padrão do QR Code pede 4). */
	private static final int MARGEM = 4;

	private static final int PRETO = 0x000000;
	private static final int BRANCO = 0xFFFFFF;

	/** Falha ao gerar a imagem. O cadastro continua salvo; a impressão pode ser repetida. */
	public static class QrCodeNaoGerado extends RuntimeException {

		private static final long serialVersionUID = 1L;

		QrCodeNaoGerado(Throwable causa) {
			super("Não foi possível gerar o QR Code", causa);
		}
	}

	public byte[] png(String codigo) {
		try {
			BitMatrix matriz = new QRCodeWriter().encode(codigo, BarcodeFormat.QR_CODE, LADO, LADO,
					Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, MARGEM));
			BufferedImage imagem = new BufferedImage(matriz.getWidth(), matriz.getHeight(), BufferedImage.TYPE_INT_RGB);
			for (int x = 0; x < matriz.getWidth(); x++) {
				for (int y = 0; y < matriz.getHeight(); y++) {
					imagem.setRGB(x, y, matriz.get(x, y) ? PRETO : BRANCO);
				}
			}
			ByteArrayOutputStream saida = new ByteArrayOutputStream();
			ImageIO.write(imagem, "png", saida);
			return saida.toByteArray();
		} catch (WriterException | IOException falha) {
			throw new QrCodeNaoGerado(falha);
		}
	}

}
