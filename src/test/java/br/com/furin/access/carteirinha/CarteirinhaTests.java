package br.com.furin.access.carteirinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.portaria.CarteirinhasFicticias;
import br.com.furin.access.portaria.CarteirinhasFicticias.CarteirinhaParaSimular;

/**
 * Impressão, QR Code e segunda via da carteirinha (T-22; RF07; RN15; CA-RF07-02 a 07). No
 * perfil {@code prototipo}, para conferir também o painel "Simular leitura", com a carga
 * fictícia desligada. Cada teste roda na transação do teste, desfeita no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class CarteirinhaTests {

	private static final String CODIGO = "c0ffee00c0ffee00c0ffee00c0ffee01";
	private static final String MATRICULA = "T22-000001";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CarteirinhasFicticias painelDeSimulacao;

	private long aluno;

	private long carteirinha;

	@BeforeEach
	void preparar() {
		LocalDateTime ontem = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES ('6A-T22', 2026, 'MANHA', 1)");
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Ana Teste', ?, 1, ?)",
				MATRICULA, UUID.randomUUID() + ".png", ontem);
		aluno = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class, MATRICULA);
		jdbc.update("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (?, "
				+ "(SELECT id_turma FROM turma WHERE nome = '6A-T22'), ?)", aluno, ontem);
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (?, ?, ?)", aluno, CODIGO, ontem);
		carteirinha = jdbc.queryForObject("SELECT id_carteirinha FROM carteirinha WHERE codigo = ?", Long.class, CODIGO);
	}

	// --- QR Code (CA-RF07-02 e 03) ---

	@Test
	void qrCodeDecodificadoDevolveExatamenteOCodigo() throws Exception {
		// CA-RF07-02: no protótipo não há leitor USB (DEC-25); o teste decodifica com ZXing
		Result lido = decodificar(qrCode(carteirinha));

		assertThat(lido.getText()).isEqualTo(CODIGO);
		// DEC-28, D9: nível de correção de erro M
		assertThat(lido.getResultMetadata()).containsEntry(ResultMetadataType.ERROR_CORRECTION_LEVEL, "M");
	}

	@Test
	void qrCodeNaoTemDadoPessoal() throws Exception {
		// CA-RF07-03: só os 32 caracteres hexadecimais; nem nome, nem matrícula, nem turma, nem URL
		String conteudo = decodificar(qrCode(carteirinha)).getText();

		assertThat(conteudo).matches("[0-9a-f]{32}").doesNotContain("Ana", MATRICULA, "6A", "http");
	}

	// --- Impressão (CA-RF07-07; DEC-28, D8) ---

	@Test
	void paginaDeImpressaoTemTituloNomeTurmaEQrCodeSemOCodigoEmTexto() throws Exception {
		mvc.perform(get("/carteirinhas/" + carteirinha + "/imprimir").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Carteirinha de Ana Teste | Furin Access</title>")))
				.andExpect(content().string(containsString("alt=\"QR Code da carteirinha de Ana Teste\"")))
				.andExpect(content().string(containsString("6A-T22 — Manhã")))
				.andExpect(content().string(containsString("class=\"carteirinha\"")))
				// Sem o código em texto e sem foto
				.andExpect(content().string(not(containsString(CODIGO))))
				.andExpect(content().string(not(containsString("/arquivos/"))));
	}

	@Test
	void carteirinhaRevogadaNaoEhImpressa() throws Exception {
		jdbc.update("UPDATE carteirinha SET revogada_em = UTC_TIMESTAMP(6) WHERE id_carteirinha = ?", carteirinha);

		mvc.perform(get("/carteirinhas/" + carteirinha + "/imprimir").with(funcionario()))
				.andExpect(content().string(containsString("Esta carteirinha foi revogada")))
				.andExpect(content().string(not(containsString("QR Code da carteirinha"))));
		mvc.perform(get("/carteirinhas/" + carteirinha + "/qr-code").with(funcionario()))
				.andExpect(status().isNotFound());
	}

	// --- Segunda via (CA-RF07-04 e 05) ---

	@Test
	void segundaViaPedeConfirmacaoAntesDeRevogar() throws Exception {
		// CA-RF07-05; A11 §5.9.12: abrir a confirmação não muda nada
		mvc.perform(get("/carteirinhas/" + carteirinha + "/imprimir").with(funcionario()))
				.andExpect(content().string(containsString("href=\"/alunos/" + aluno + "/carteirinhas/segunda-via\"")));
		mvc.perform(get("/alunos/" + aluno + "/carteirinhas/segunda-via").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("A carteirinha atual deixará de funcionar. Confirmar?")))
				.andExpect(content().string(containsString("Confirmar segunda via")));

		assertThat(validas()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT revogada_em FROM carteirinha WHERE id_carteirinha = ?",
				LocalDateTime.class, carteirinha)).isNull();
	}

	@Test
	void segundaViaRevogaAAnteriorEDeixaUmaValida() throws Exception {
		// CA-RF07-04; RN15
		String destino = mvc.perform(post("/alunos/" + aluno + "/carteirinhas").with(funcionario()).with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();

		assertThat(validas()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT revogada_em FROM carteirinha WHERE id_carteirinha = ?",
				LocalDateTime.class, carteirinha)).isNotNull();
		long nova = jdbc.queryForObject("SELECT id_carteirinha FROM carteirinha WHERE id_aluno = ? "
				+ "AND revogada_em IS NULL", Long.class, aluno);
		assertThat(destino).isEqualTo("/carteirinhas/" + nova + "/imprimir");
		assertThat(jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_carteirinha = ?", String.class, nova))
				.matches("[0-9a-f]{32}").isNotEqualTo(CODIGO);
	}

	@Test
	void depoisDaSegundaViaACarteirinhaAntigaEhRecusadaEOPainelUsaANova() throws Exception {
		// CA-RF07-04; RF23: a leitura da antiga é recusada, e o "Simular leitura" envia o código novo
		mvc.perform(post("/alunos/" + aluno + "/carteirinhas").with(funcionario()).with(csrf()))
				.andExpect(status().is3xxRedirection());
		String novo = jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				String.class, aluno);

		mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + CODIGO + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("CARTEIRINHA_REVOGADA"));
		assertThat(painelDeSimulacao.ativas()).extracting(CarteirinhaParaSimular::codigo)
				.contains(novo).doesNotContain(CODIGO);
	}

	@Test
	void segundaViaVaiParaOLogSoComOsIds(CapturedOutput saida) throws Exception {
		mvc.perform(post("/alunos/" + aluno + "/carteirinhas").with(funcionario()).with(csrf()));
		String novo = jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				String.class, aluno);

		assertThat(saida.getOut()).contains("evento=CARTEIRINHA_SEGUNDA_VIA", "aluno=" + aluno)
				.doesNotContain(CODIGO, novo, "Ana Teste");
	}

	@Test
	void alunoInativoNaoRecebeSegundaVia() throws Exception {
		// RF07, exceções
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", aluno);

		mvc.perform(post("/alunos/" + aluno + "/carteirinhas").with(funcionario()).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Não é possível emitir carteirinha para aluno inativo.")));

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carteirinha WHERE id_aluno = ?", Integer.class, aluno))
				.isEqualTo(1);
		assertThat(validas()).isEqualTo(1);
	}

	// --- Acesso (CA-RF07-06) ---

	@Test
	void semLoginAEmissaoEAImpressaoLevamAoLogin() throws Exception {
		mvc.perform(get("/carteirinhas/" + carteirinha + "/imprimir")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/carteirinhas/" + carteirinha + "/qr-code")).andExpect(redirectedUrl("/login"));
		mvc.perform(get("/alunos/" + aluno + "/carteirinhas/segunda-via")).andExpect(redirectedUrl("/login"));
		mvc.perform(post("/alunos/" + aluno + "/carteirinhas").with(csrf())).andExpect(redirectedUrl("/login"));

		assertThat(validas()).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				String.class, aluno)).isEqualTo(CODIGO);
	}

	@Test
	void carteirinhaQueNaoExisteDa404() throws Exception {
		mvc.perform(get("/carteirinhas/999999999/imprimir").with(funcionario())).andExpect(status().isNotFound());
	}

	// --- Apoio ---

	private byte[] qrCode(long id) throws Exception {
		return mvc.perform(get("/carteirinhas/" + id + "/qr-code").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andReturn().getResponse().getContentAsByteArray();
	}

	private static Result decodificar(byte[] png) throws Exception {
		BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(png));
		return new MultiFormatReader()
				.decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(imagem))));
	}

	private int validas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				Integer.class, aluno);
	}

	private static RequestPostProcessor funcionario() {
		return user("carteirinha.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
