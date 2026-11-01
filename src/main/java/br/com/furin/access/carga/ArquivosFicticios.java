package br.com.furin.access.carga;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Arquivos fictícios da carga do protótipo (arquitetura §5.6; A10 §8.33): uma silhueta
 * desenhada com formas simples, sem fonte e sem foto de ninguém, e um termo de exemplo
 * em PDF, só com texto ASCII.
 */
final class ArquivosFicticios {

	private static final int LARGURA = 240;
	private static final int ALTURA = 300;

	private ArquivosFicticios() {
	}

	/** Silhueta em PNG: cabeça e ombros sobre um fundo claro. */
	static byte[] silhuetaPng(Color fundo) {
		BufferedImage imagem = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
		Graphics2D desenho = imagem.createGraphics();
		try {
			desenho.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			desenho.setColor(fundo);
			desenho.fillRect(0, 0, LARGURA, ALTURA);
			desenho.setColor(new Color(0x59, 0x6B, 0x7A));
			desenho.fillOval(75, 50, 90, 100);
			desenho.fillOval(30, 165, 180, 200);
		} finally {
			desenho.dispose();
		}
		ByteArrayOutputStream saida = new ByteArrayOutputStream();
		try {
			ImageIO.write(imagem, "png", saida);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return saida.toByteArray();
	}

	/** PDF de uma página com o texto do termo de exemplo (PDF 1.4 mínimo, montado à mão). */
	static byte[] termoPdf(String versaoTermo) {
		String texto = "BT /F1 14 Tf 72 760 Td (Termo de consentimento - EXEMPLO FICTICIO) Tj "
				+ "0 -24 Td (Versao " + versaoTermo + ". Furin Access, carga do prototipo.) Tj "
				+ "0 -24 Td (Nenhum dado real. Documento gerado automaticamente.) Tj ET";
		List<String> objetos = List.of(
				"<< /Type /Catalog /Pages 2 0 R >>",
				"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
				"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
						+ "/Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>",
				"<< /Length " + texto.length() + " >>\nstream\n" + texto + "\nendstream",
				"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

		StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
		List<Integer> posicoes = new ArrayList<>();
		for (int i = 0; i < objetos.size(); i++) {
			posicoes.add(pdf.length());
			pdf.append(i + 1).append(" 0 obj\n").append(objetos.get(i)).append("\nendobj\n");
		}
		int inicioXref = pdf.length();
		pdf.append("xref\n0 ").append(objetos.size() + 1).append("\n0000000000 65535 f \n");
		for (int posicao : posicoes) {
			// Cada entrada da tabela xref tem exatamente 20 bytes
			pdf.append(String.format("%010d", posicao)).append(" 00000 n \n");
		}
		pdf.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\n")
				.append("startxref\n").append(inicioXref).append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.US_ASCII);
	}

}
