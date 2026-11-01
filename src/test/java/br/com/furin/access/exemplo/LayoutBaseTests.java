package br.com.furin.access.exemplo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlSelect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;

import br.com.furin.access.auditoria.AcessoNegadoAuditado;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.config.RelogioConfig;
import br.com.furin.access.config.SegurancaConfig;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * Confere o layout-base pela página de exemplo, lida como um navegador (HtmlUnit):
 * os itens automatizáveis da checklist de acessibilidade do plano §3 (T-08; RNF06,
 * RF03). Contraste, foco e alvos ficam em {@link EstiloBaseTests}.
 */
@WebMvcTest(ExemploLayoutController.class)
@Import({ SegurancaConfig.class, AcessoNegadoAuditado.class, Auditoria.class, RelogioConfig.class })
@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
class LayoutBaseTests {

	private static final String URL = "http://localhost/exemplo/layout";

	@Autowired
	private WebClient navegador;

	@BeforeEach
	void configurarNavegador() {
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
	}

	@Test
	void temIdiomaETituloNoFormatoDaA11() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		assertThat(pagina.getDocumentElement().getAttribute("lang")).isEqualTo("pt-BR");
		assertThat(pagina.getTitleText()).isEqualTo("Exemplo de layout | Furin Access");
	}

	@Test
	void naoBloqueiaOZoom() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		DomElement viewport = pagina.getElementsByTagName("meta").stream()
				.filter(meta -> "viewport".equals(meta.getAttribute("name")))
				.findFirst().orElseThrow();
		assertThat(viewport.getAttribute("content"))
				.contains("width=device-width")
				.doesNotContain("user-scalable")
				.doesNotContain("maximum-scale");
	}

	@Test
	void primeiroElementoFocavelLevaAoConteudoPrincipal() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		HtmlElement primeiro = pagina.getTabbableElements().getFirst();
		assertThat(primeiro.getTagName()).isEqualTo("a");
		assertThat(primeiro.getTextContent().trim()).isEqualTo("Ir para o conteúdo principal");
		assertThat(primeiro.getAttribute("href")).isEqualTo("#conteudo-principal");
		assertThat(pagina.getElementById("conteudo-principal").getTagName()).isEqualTo("main");
	}

	@Test
	void menuTemOsItensNaOrdemDoRF03() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		DomElement menu = pagina.querySelector("nav[aria-label='Menu principal']");
		List<String> itens = menu.querySelectorAll("a, button").stream()
				.map(item -> item.getTextContent().trim())
				.toList();
		assertThat(itens).containsExactly("Portaria", "Alunos", "Presentes", "Histórico",
				"Contas da equipe", "Alterar senha", "Sair");
		assertThat(menu.querySelectorAll("a").stream().map(a -> ((DomElement) a).getAttribute("href")))
				.containsExactly("/portaria", "/alunos", "/presentes", "/historico",
						"/contas/equipe", "/senha/alterar");
	}

	@Test
	void paginaForaDoMenuNaoMarcaItemAtual() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		assertThat(pagina.querySelectorAll("nav [aria-current]")).isEmpty();
	}

	@Test
	void sairEhFormularioPostComTokenCsrf() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		HtmlForm sair = (HtmlForm) pagina.querySelector("nav form");
		assertThat(sair.getActionAttribute()).isEqualTo("/logout");
		assertThat(sair.getMethodAttribute()).isEqualToIgnoringCase("post");
		assertThat(sair.getInputByName("_csrf").getValue()).isNotBlank();
	}

	@Test
	void temUmH1EH2PorSecao() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		assertThat(pagina.getElementsByTagName("h1")).hasSize(1);
		assertThat(pagina.querySelectorAll("main section"))
				.allSatisfy(secao -> assertThat(((DomElement) secao).querySelectorAll("h2")).hasSize(1));
	}

	@Test
	void todoCampoTemRotulo() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		assertThat(pagina.querySelectorAll("main input:not([type=hidden]), main select, main textarea"))
				.isNotEmpty()
				.allSatisfy(campo -> {
					String id = ((DomElement) campo).getId();
					assertThat(id).isNotBlank();
					DomElement rotulo = pagina.querySelector("label[for='" + id + "']");
					assertThat(rotulo).as("label do campo %s", id).isNotNull();
				});
		assertThat(pagina.querySelector("label[for='nome']").getTextContent()).contains("(obrigatório)");
	}

	@Test
	void erroDescritivoPreservaOsDados() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		HtmlForm formulario = (HtmlForm) pagina.querySelector("main form");
		((HtmlSelect) formulario.getSelectByName("turma")).setSelectedAttribute("7B", true);

		HtmlPage resposta = formulario.<HtmlButton>getFirstByXPath(".//button[@type='submit']").click();

		HtmlInput nome = (HtmlInput) resposta.getElementById("nome");
		assertThat(nome.getAttribute("aria-invalid")).isEqualTo("true");
		assertThat(nome.getAttribute("aria-describedby")).contains("nome-erro");
		assertThat(resposta.getElementById("nome-erro").getTextContent())
				.contains("Erro:").contains("Informe o nome do aluno.");
		assertThat(((HtmlSelect) resposta.getElementById("turma")).getSelectedOptions())
				.extracting(opcao -> opcao.getValueAttribute()).containsExactly("7B");
		assertThat(resposta.querySelector("[role=status]").getTextContent()).doesNotContain("Sucesso");
	}

	@Test
	void envioValidoAvisaEmRoleStatus() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		HtmlForm formulario = (HtmlForm) pagina.querySelector("main form");
		formulario.getInputByName("nome").type("Ana Teste");

		HtmlPage resposta = formulario.<HtmlButton>getFirstByXPath(".//button[@type='submit']").click();

		assertThat(resposta.querySelector("[role=status]").getTextContent()).contains("Sucesso:");
		HtmlInput nome = (HtmlInput) resposta.getElementById("nome");
		assertThat(nome.getValue()).isEqualTo("Ana Teste");
		assertThat(nome.hasAttribute("aria-invalid")).isFalse();
		assertThat(nome.getAttribute("aria-describedby")).isEqualTo("nome-dica");
	}

	@Test
	void tabelaTemLegendaECabecalhos() throws Exception {
		HtmlPage pagina = navegador.getPage(URL);
		DomElement legenda = pagina.querySelector("table caption");
		assertThat(legenda).isNotNull();
		assertThat(pagina.querySelectorAll("thead th")).allSatisfy(
				th -> assertThat(((DomElement) th).getAttribute("scope")).isEqualTo("col"));
		assertThat(pagina.querySelectorAll("tbody th")).allSatisfy(
				th -> assertThat(((DomElement) th).getAttribute("scope")).isEqualTo("row"));
	}

}
