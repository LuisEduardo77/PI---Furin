package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlCheckBoxInput;
import org.htmlunit.html.HtmlLabel;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlFileInput;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * O formulário "Novo aluno" como no navegador, num Tomcat de verdade (T-19; CA-RF04-08 e
 * 09): só assim o envio de arquivo passa pelo limite de {@code spring.servlet.multipart}
 * e pela leitura do token CSRF, que o MockMvc pula.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FormularioDeAlunoTests {

	private static final String FUNCIONARIO = "formulario.teste";
	private static final String SENHA = "descartavel-formulario-T19";
	private static final String TURMA = "6A-T19-TELA";

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-formulario-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", AREA_PRIVADA::toString);
	}

	@AfterAll
	static void apagarAAreaPrivada() throws IOException {
		FileSystemUtils.deleteRecursively(AREA_PRIVADA);
	}

	@LocalServerPort
	private int porta;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	private WebClient navegador;

	@BeforeEach
	void preparar() throws Exception {
		apagarACena();
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', ?, ?, ?, 1, 0, NULL, ?)",
				FUNCIONARIO, encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO,
				LocalDateTime.now(ZoneOffset.UTC));
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", TURMA);
		navegador = new WebClient();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);
	}

	@AfterEach
	void apagarACena() {
		if (navegador != null) {
			navegador.close();
		}
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	@Test
	void ordemDeFocoSegueOCriterioDoRf04() throws Exception {
		HtmlPage pagina = abrirOFormulario();

		// CA-RF04-08: Nome → Matrícula → Turma → Foto → Contato → Termo → Salvar → Cancelar
		List<String> formulario = pagina.getTabbableElements().stream()
				.filter(elemento -> elemento.getEnclosingForm() != null || "Cancelar".equals(elemento.getTextContent()))
				.map(FormularioDeAlunoTests::nomeNaOrdem)
				// Sem o "Sair" do menu e sem os campos ocultos do token CSRF e da caixa de seleção
				.filter(nome -> !nome.isEmpty() && !nome.equals("Sair"))
				.toList();

		assertThat(formulario).containsExactly("nome", "matricula", "idTurma", "foto", "contatoNome",
				"contatoTelefone", "contatoEmail", "termoSignatario", "termoVersao", "termoAssinadoEm",
				"termoProtocolo", "termoArquivo", "termoConferido", "Salvar", "Cancelar");
	}

	@Test
	void todoCampoTemLabelAssociado() throws Exception {
		HtmlPage pagina = abrirOFormulario();

		for (String campo : List.of("nome", "matricula", "idTurma", "foto", "contatoNome", "contatoTelefone",
				"contatoEmail", "termoSignatario", "termoVersao", "termoAssinadoEm", "termoProtocolo", "termoArquivo",
				"termoConferido")) {
			assertThat(pagina.querySelectorAll("label[for='" + campo + "']")).as("label de " + campo).hasSize(1);
		}
	}

	@Test
	void clicarNoTextoMarcaACaixaDoTermo() throws Exception {
		// CA-RF05-07: o texto é o label da caixa, e diz que ela é obrigatória
		HtmlPage pagina = abrirOFormulario();
		HtmlCheckBoxInput caixa = (HtmlCheckBoxInput) pagina.getElementById("termoConferido");
		HtmlLabel texto = (HtmlLabel) pagina.querySelector("label[for='termoConferido']");
		assertThat(caixa.isChecked()).isFalse();

		texto.click();

		assertThat(caixa.isChecked()).isTrue();
		assertThat(texto.getTextContent()).contains("(obrigatório)");
	}

	@Test
	void comErroOFocoVaiParaOResumoEODadoValidoFica() throws Exception {
		// CA-RF04-09
		HtmlPage pagina = abrirOFormulario();
		campo(pagina, "nome").setValue("Ana Teste");

		HtmlPage resposta = salvar(pagina);

		DomElement resumo = resposta.getElementById("resumo-erros");
		assertThat(resumo).isNotNull();
		assertThat(resposta.getFocusedElement()).isEqualTo(resumo);
		assertThat(campo(resposta, "nome").getValue()).isEqualTo("Ana Teste");
		assertThat(campo(resposta, "matricula").getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(campo(resposta, "matricula").getAttribute("aria-describedby")).isEqualTo("matricula-erro");
	}

	@Test
	void fotoEnviadaPassaPeloTokenCsrfENadaESalvo() throws Exception {
		HtmlPage resposta = salvar(preenchido(foto(PNG)));

		assertThat(textoDoMain(resposta)).contains("Dados conferidos");
		assertThat(resposta.getElementById("resumo-erros")).isNull();
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = 'T19-TELA'", Integer.class)).isZero();
	}

	@Test
	void fotoAcimaDoLimiteMostraOErroNoCampo() throws Exception {
		// 3 MB: acima de furin.arquivos.foto.tamanho-max e abaixo do limite do envio
		HtmlPage resposta = salvar(preenchido(foto(Arrays.copyOf(PNG, 3 * 1024 * 1024))));

		assertThat(resposta.getWebResponse().getStatusCode()).isEqualTo(200);
		assertThat(resposta.getElementById("foto-erro").getTextContent())
				.contains("A foto precisa ser JPEG ou PNG, até 2 MB.");
	}

	// --- Apoio ---

	private HtmlPage preenchido(File foto) throws Exception {
		HtmlPage pagina = abrirOFormulario();
		campo(pagina, "nome").setValue("Ana Teste");
		campo(pagina, "matricula").setValue("T19-TELA");
		HtmlSelect turma = (HtmlSelect) pagina.getElementById("idTurma");
		turma.getOptions().stream().filter(opcao -> opcao.getText().startsWith(TURMA)).findFirst().orElseThrow()
				.setSelected(true);
		((HtmlFileInput) pagina.getElementById("foto")).setFiles(foto);
		campo(pagina, "contatoNome").setValue("Carla Teste");
		campo(pagina, "contatoTelefone").setValue("(11) 90000-0001");
		campo(pagina, "contatoEmail").setValue("carla.teste@example.com");
		campo(pagina, "termoAssinadoEm").setValue(LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA).minusDays(1).toString());
		campo(pagina, "termoProtocolo").setValue("TERMO-0001");
		((HtmlCheckBoxInput) pagina.getElementById("termoConferido")).setChecked(true);
		return pagina;
	}

	private HtmlPage abrirOFormulario() throws Exception {
		HtmlPage login = navegador.getPage(endereco("/login"));
		((HtmlInput) login.getElementById("usuario")).setValue(FUNCIONARIO);
		((HtmlInput) login.getElementById("senha")).setValue(SENHA);
		((HtmlButton) login.querySelector("main form button[type=submit]")).click();
		return navegador.getPage(endereco("/alunos/novo"));
	}

	private static HtmlPage salvar(HtmlPage pagina) throws IOException {
		HtmlPage resposta = ((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
		resposta.getWebClient().waitForBackgroundJavaScript(500);
		return resposta;
	}

	private File foto(byte[] conteudo) throws IOException {
		Files.createDirectories(AREA_PRIVADA);
		Path temporaria = Files.createTempFile(AREA_PRIVADA, "foto-teste", ".png");
		Files.write(temporaria, conteudo);
		return temporaria.toFile();
	}

	private static HtmlInput campo(HtmlPage pagina, String id) {
		return (HtmlInput) pagina.getElementById(id);
	}

	private static String nomeNaOrdem(HtmlElement elemento) {
		String id = elemento.getId();
		return id.isEmpty() ? elemento.getTextContent().trim() : id;
	}

	private static String textoDoMain(HtmlPage pagina) {
		return pagina.getElementById("conteudo-principal").getTextContent().replaceAll("\\s+", " ");
	}

	private String endereco(String caminho) {
		return URI.create("http://localhost:" + porta + caminho).toString();
	}

}
