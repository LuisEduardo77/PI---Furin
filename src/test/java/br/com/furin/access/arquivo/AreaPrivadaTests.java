package br.com.furin.access.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.util.unit.DataSize;

import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.arquivo.ArquivoRecusado.Motivo;
import br.com.furin.access.config.FurinProperties.Arquivos;
import br.com.furin.access.config.FurinProperties.RegraArquivo;

/**
 * Regras da área privada (T-18; DEC-08; DEC-28, D1 e D2), com os mesmos valores do
 * application.yml e um diretório temporário.
 */
class AreaPrivadaTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0 };
	private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

	private static final DataSize DOIS_MB = DataSize.ofMegabytes(2);
	private static final DataSize CINCO_MB = DataSize.ofMegabytes(5);

	@TempDir
	private Path diretorio;

	private AreaPrivada area;

	@BeforeEach
	void preparar() {
		area = new AreaPrivada(new Arquivos(diretorio,
				new RegraArquivo(List.of("image/jpeg", "image/png"), DOIS_MB),
				new RegraArquivo(List.of("application/pdf", "image/jpeg", "image/png"), CINCO_MB)));
	}

	@Test
	void guardaComNomeUuidEAExtensaoDoConteudo() throws IOException {
		String png = guardar(TipoDeDocumento.FOTO, PNG);
		String jpeg = guardar(TipoDeDocumento.FOTO, JPEG);
		String pdf = guardar(TipoDeDocumento.TERMO, PDF);

		assertThat(png).matches("[0-9a-f-]{36}\\.png");
		assertThat(jpeg).matches("[0-9a-f-]{36}\\.jpg");
		assertThat(pdf).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(diretorio.resolve(png)).hasBinaryContent(PNG);
	}

	@Test
	void entregaOArquivoComOTipoDoConteudo() throws IOException {
		String chave = guardar(TipoDeDocumento.TERMO, PDF);

		assertThat(area.abrir(chave)).hasValueSatisfying(arquivo -> {
			assertThat(arquivo.mime()).isEqualTo("application/pdf");
			assertThat(arquivo.conteudo().getByteArray()).isEqualTo(PDF);
		});
	}

	@Test
	void naEntregaOTipoVemDoConteudoENaoDaExtensao() throws IOException {
		String chaveComExtensaoErrada = "00000000-0000-0000-0000-000000000001.png";
		Files.write(diretorio.resolve(chaveComExtensaoErrada), PDF);

		assertThat(area.abrir(chaveComExtensaoErrada)).hasValueSatisfying(
				arquivo -> assertThat(arquivo.mime()).isEqualTo("application/pdf"));
	}

	@Test
	void textoComNomeDeImagemNaoEntra() {
		// A entrada nem traz nome: o que decide é o conteúdo
		assertRecusado(TipoDeDocumento.FOTO, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8),
				Motivo.TIPO_NAO_ACEITO);
		assertRecusado(TipoDeDocumento.TERMO, new byte[0], Motivo.TIPO_NAO_ACEITO);
	}

	@Test
	void fotoNaoAceitaPdfMasOTermoAceita() throws IOException {
		assertRecusado(TipoDeDocumento.FOTO, PDF, Motivo.TIPO_NAO_ACEITO);
		assertThat(guardar(TipoDeDocumento.TERMO, PDF)).endsWith(".pdf");
	}

	@Test
	void respeitaOTamanhoMaximoDeCadaTipo() throws IOException {
		assertThat(guardar(TipoDeDocumento.FOTO, comTamanho(PNG, DOIS_MB.toBytes()))).endsWith(".png");
		assertRecusado(TipoDeDocumento.FOTO, comTamanho(PNG, DOIS_MB.toBytes() + 1), Motivo.TAMANHO_EXCEDIDO);

		assertThat(guardar(TipoDeDocumento.TERMO, comTamanho(PDF, CINCO_MB.toBytes()))).endsWith(".pdf");
		assertRecusado(TipoDeDocumento.TERMO, comTamanho(PDF, CINCO_MB.toBytes() + 1), Motivo.TAMANHO_EXCEDIDO);
	}

	@Test
	void arquivoRecusadoNaoFicaNoDiretorio() throws IOException {
		assertRecusado(TipoDeDocumento.FOTO, PDF, Motivo.TIPO_NAO_ACEITO);

		try (var arquivos = Files.list(diretorio)) {
			assertThat(arquivos).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "1", "..", "../segredo.png", "..\\segredo.png", "nao-e-uuid.png",
			"00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000001.exe",
			"00000000-0000-0000-0000-00000000000A.png", "00000000-0000-0000-0000-000000000001.png/../x.png" })
	void chaveForaDoFormatoNuncaChegaAoDisco(String chave) {
		assertThat(area.chaveValida(chave)).isFalse();
		assertThat(area.abrir(chave)).isEmpty();
	}

	@Test
	void arquivoQueNaoExisteVoltaVazio() {
		assertThat(area.abrir("00000000-0000-0000-0000-000000000001.png")).isEmpty();
	}

	@Test
	void arquivoDeFormatoDesconhecidoNaoEEntregue() throws IOException {
		String chave = "00000000-0000-0000-0000-000000000002.png";
		Files.writeString(diretorio.resolve(chave), "<html>");

		assertThat(area.abrir(chave)).isEmpty();
	}

	private String guardar(TipoDeDocumento documento, byte[] conteudo) throws IOException {
		return area.guardar(documento, new ByteArrayInputStream(conteudo));
	}

	private void assertRecusado(TipoDeDocumento documento, byte[] conteudo, Motivo motivo) {
		assertThatThrownBy(() -> guardar(documento, conteudo))
				.isInstanceOfSatisfying(ArquivoRecusado.class, recusa -> assertThat(recusa.getMotivo()).isEqualTo(motivo));
	}

	private static byte[] comTamanho(byte[] inicio, long tamanho) {
		return Arrays.copyOf(inicio, Math.toIntExact(tamanho));
	}

}
