package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.htmlunit.HttpMethod;
import org.htmlunit.WebClient;
import org.htmlunit.WebRequest;
import org.htmlunit.html.HtmlAnchor;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Página 403 acessível (T-17; RF03, CA-RF03-06), com o Tomcat de verdade: o MockMvc não
 * passa pela página de erro do Spring Boot. O 403 aqui vem de um POST sem token CSRF,
 * que não precisa de login.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PaginaDeAcessoNegadoTests {

	@LocalServerPort
	private int porta;

	@Test
	void respostaTem403ETituloDaPagina() throws Exception {
		HttpResponse<String> resposta = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(endereco("/portaria")))
						// Como o navegador: pede HTML (sem isso, o Spring Boot responde o erro em JSON)
						.header("Accept", "text/html")
						.POST(HttpRequest.BodyPublishers.noBody()).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.statusCode()).isEqualTo(403);
		assertThat(resposta.body()).contains("<title>Acesso não permitido | Furin Access</title>");
	}

	@Test
	void paginaTemTituloH1TextoELinkDeVoltaPeloTeclado() throws Exception {
		try (WebClient navegador = new WebClient()) {
			navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);
			navegador.getOptions().setCssEnabled(false);
			navegador.getOptions().setJavaScriptEnabled(false);
			WebRequest pedido = new WebRequest(URI.create(endereco("/portaria")).toURL(), HttpMethod.POST);
			pedido.setAdditionalHeader("Accept", "text/html");
			HtmlPage pagina = navegador.getPage(pedido);

			assertThat(pagina.getWebResponse().getStatusCode()).isEqualTo(403);
			assertThat(pagina.getTitleText()).isEqualTo("Acesso não permitido | Furin Access");
			assertThat(pagina.getDocumentElement().getAttribute("lang")).isEqualTo("pt-BR");
			assertThat(pagina.getElementsByTagName("h1")).singleElement()
					.extracting(elemento -> elemento.getTextContent().trim()).isEqualTo("Acesso não permitido");
			assertThat(pagina.querySelector("main p").getTextContent()).contains("Acesso não permitido.");

			// O link de volta é um <a href>, alcançado pelo Tab (CA-RF03-06). Sem ;jsessionid
			// no endereço: a sessão só anda no cookie
			HtmlAnchor voltar = pagina.getAnchorByText("Voltar ao início");
			assertThat(voltar.getHrefAttribute()).isEqualTo(SegurancaConfig.TELA_INICIAL);
			List<HtmlElement> tabulaveis = pagina.getTabbableElements();
			assertThat(tabulaveis).contains(voltar);
			// CA-RF03-07: o primeiro elemento focável é o "Ir para o conteúdo principal"
			assertThat(tabulaveis.getFirst().getTextContent().trim()).isEqualTo("Ir para o conteúdo principal");
		}
	}

	@Test
	void rotaInexistenteMostraAPaginaNaoEncontrada() throws Exception {
		// RF03, exceções. Um arquivo estático que não existe dá 404 sem precisar de login
		try (WebClient navegador = new WebClient()) {
			navegador.getOptions().setThrowExceptionOnFailingStatusCode(false);
			navegador.getOptions().setCssEnabled(false);
			navegador.getOptions().setJavaScriptEnabled(false);
			WebRequest pedido = new WebRequest(URI.create(endereco("/css/nao-existe.css")).toURL());
			pedido.setAdditionalHeader("Accept", "text/html");
			HtmlPage pagina = navegador.getPage(pedido);

			assertThat(pagina.getWebResponse().getStatusCode()).isEqualTo(404);
			assertThat(pagina.getTitleText()).isEqualTo("Página não encontrada | Furin Access");
			assertThat(pagina.querySelector("main p").getTextContent()).contains("Página não encontrada.");
			assertThat(pagina.getAnchorByText("Voltar ao início").getHrefAttribute())
					.isEqualTo(SegurancaConfig.TELA_INICIAL);
		}
	}

	private String endereco(String caminho) {
		return "http://localhost:" + porta + caminho;
	}

}
