package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cookie de sessão com HttpOnly e SameSite=Lax (T-09; arquitetura §6; RNF04).
 * Precisa do servidor de verdade: os atributos do cookie vêm do Tomcat, não do MockMvc.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CookieDeSessaoTests {

	@LocalServerPort
	private int porta;

	@Test
	void cookieDeSessaoEhHttpOnlyESameSiteLax() throws Exception {
		// A tela de login guarda o token CSRF na sessão, e a sessão cria o cookie
		HttpResponse<Void> resposta = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/login")).build(),
				HttpResponse.BodyHandlers.discarding());

		List<String> cookies = resposta.headers().allValues("Set-Cookie");
		String sessao = cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst()
				.orElseThrow(() -> new AssertionError("Sem cookie de sessão: " + cookies));
		// Secure só quando houver HTTPS (RNF04 planejado; DEC-31)
		assertThat(sessao).containsIgnoringCase("HttpOnly")
				.containsIgnoringCase("SameSite=Lax")
				.doesNotContainIgnoringCase("Secure");
	}

	@Test
	void identificadorDaSessaoNuncaVaiNoEndereco() throws Exception {
		// T-17: na primeira visita, sem tracking-modes=cookie, o Tomcat reescrevia os links
		HttpResponse<String> resposta = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/login")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(resposta.body()).doesNotContainIgnoringCase("jsessionid");
	}

}
