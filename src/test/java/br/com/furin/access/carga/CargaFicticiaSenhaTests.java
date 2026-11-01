package br.com.furin.access.carga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import br.com.furin.access.config.FurinProperties;

/**
 * Regras da carga que não precisam do banco (T-11): a senha vem do ambiente, com o
 * tamanho mínimo, e a mensagem de erro nunca mostra o valor. Também confere os
 * arquivos fictícios gerados.
 */
class CargaFicticiaSenhaTests {

	private final MockEnvironment ambiente = new MockEnvironment();
	private final V2__CargaFicticia carga = new V2__CargaFicticia(
			PasswordEncoderFactories.createDelegatingPasswordEncoder(), propriedades(), Clock.systemUTC(), ambiente);

	@Test
	void semAVariavelACargaFalhaDizendoQualFalta() {
		assertThatThrownBy(() -> carga.senhaDoAmbiente(V2__CargaFicticia.VARIAVEL_SENHA_ATE))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FURIN_SENHA_ATE_DEMO");
	}

	@Test
	void senhaCurtaEhRecusadaSemMostrarOValor() {
		ambiente.setProperty(V2__CargaFicticia.VARIAVEL_SENHA_GESTAO, "curta12");
		assertThatThrownBy(() -> carga.senhaDoAmbiente(V2__CargaFicticia.VARIAVEL_SENHA_GESTAO))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("FURIN_SENHA_GESTAO_DEMO")
				.hasMessageContaining("8 caracteres")
				.message().doesNotContain("curta12");
	}

	@Test
	void senhaComOTamanhoMinimoEhAceita() {
		ambiente.setProperty(V2__CargaFicticia.VARIAVEL_SENHA_ATE, "oito-chr");
		assertThat(carga.senhaDoAmbiente(V2__CargaFicticia.VARIAVEL_SENHA_ATE)).isEqualTo("oito-chr");
	}

	@Test
	void fotoFicticiaEhUmPngValido() throws IOException {
		byte[] png = ArquivosFicticios.silhuetaPng(java.awt.Color.WHITE);
		assertThat(ImageIO.read(new ByteArrayInputStream(png)))
				.satisfies(imagem -> assertThat(imagem.getWidth()).isEqualTo(240));
	}

	@Test
	void termoFicticioEhUmPdfComTabelaXrefCorreta() {
		String pdf = new String(ArquivosFicticios.termoPdf("v1-2026"), StandardCharsets.US_ASCII);
		assertThat(pdf).startsWith("%PDF-1.4").endsWith("%%EOF\n").contains("Versao v1-2026");
		// startxref aponta exatamente para a palavra "xref"
		int inicioXref = Integer.parseInt(pdf.substring(pdf.indexOf("startxref\n") + 10, pdf.lastIndexOf("\n%%EOF")));
		assertThat(pdf.substring(inicioXref)).startsWith("xref\n0 6\n");
		// cada objeto começa onde a tabela diz
		String[] entradas = pdf.substring(inicioXref).split("\n");
		for (int objeto = 1; objeto <= 5; objeto++) {
			int posicao = Integer.parseInt(entradas[2 + objeto].substring(0, 10));
			assertThat(pdf.substring(posicao)).startsWith(objeto + " 0 obj");
		}
	}

	private static FurinProperties propriedades() {
		FurinProperties furin = mock(FurinProperties.class);
		when(furin.senha()).thenReturn(new FurinProperties.Senha(Duration.ofHours(72), 12, 8));
		return furin;
	}

}
