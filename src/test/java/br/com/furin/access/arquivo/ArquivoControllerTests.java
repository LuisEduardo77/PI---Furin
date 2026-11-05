package br.com.furin.access.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.config.SegurancaConfig;

/** {@code GET /arquivos/{chave}} (T-18; DEC-08; CA-RF04-06, CA-RF05-06, CA-RF10-05, CA-RNF05-07). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ArquivoControllerTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
	private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

	private static final Path DIRETORIO = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-arquivos-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", DIRETORIO::toString);
	}

	@AfterAll
	static void apagarAAreaPrivada() throws IOException {
		FileSystemUtils.deleteRecursively(DIRETORIO);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AreaPrivada area;

	@Test
	void semLoginLevaAoLoginENaoEntregaOArquivo() throws Exception {
		String chave = guardar(TipoDeDocumento.FOTO, PNG);

		mvc.perform(get("/arquivos/" + chave))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"))
				.andExpect(content().bytes(new byte[0]));
	}

	@Test
	void funcionarioRecebeAFotoComOTipoDoConteudo() throws Exception {
		String chave = guardar(TipoDeDocumento.FOTO, PNG);

		mvc.perform(get("/arquivos/" + chave).with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(PNG))
				.andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "inline"))
				// O navegador não adivinha o tipo nem guarda a foto de um aluno
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
	}

	@Test
	void funcionarioRecebeOTermo() throws Exception {
		String chave = guardar(TipoDeDocumento.TERMO, PDF);

		mvc.perform(get("/arquivos/" + chave).with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.APPLICATION_PDF))
				.andExpect(content().bytes(PDF));
	}

	@Test
	void arquivoQueFaltaDa404EVaiParaOLogSoComAChave(CapturedOutput saida) throws Exception {
		// RF10; DEC-28, A9
		String chave = UUID.randomUUID() + ".png";

		mvc.perform(get("/arquivos/" + chave).with(funcionario())).andExpect(status().isNotFound());

		assertThat(saida.getOut()).contains("evento=ARQUIVO_INDISPONIVEL", "chave=" + chave);
	}

	@Test
	void chaveForaDoFormatoDa404SemIrAoLog(CapturedOutput saida) throws Exception {
		mvc.perform(get("/arquivos/nao-e-uma-chave.png").with(funcionario())).andExpect(status().isNotFound());

		assertThat(saida.getOut()).doesNotContain("ARQUIVO_INDISPONIVEL");
	}

	@Test
	void arquivosFicamForaDoProjeto() throws IOException {
		// CA-RF04-06: fora de static/ (a validação de FurinProperties também confere isso)
		String chave = guardar(TipoDeDocumento.FOTO, PNG);

		assertThat(Files.exists(DIRETORIO.resolve(chave))).isTrue();
		assertThat(DIRETORIO.toAbsolutePath().startsWith(Path.of("").toAbsolutePath())).isFalse();
	}

	private String guardar(TipoDeDocumento documento, byte[] conteudo) throws IOException {
		return area.guardar(documento, new ByteArrayInputStream(conteudo));
	}

	private static RequestPostProcessor funcionario() {
		return user("arquivo.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
