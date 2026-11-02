package br.com.furin.access.autenticacao;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Sessão expirada com o Tomcat de verdade (T-16; RF01, fluxo A2, passo 3): o MockMvc não
 * reproduz um cookie de sessão que o servidor já descartou.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SessaoExpiradaNoServidorTests {

	@LocalServerPort
	private int porta;

	private final HttpClient cliente = HttpClient.newHttpClient();

	@Test
	void cookieDeSessaoQueNaoExisteMaisLevaAoAvisoSemLaco() throws Exception {
		HttpResponse<Void> resposta = pedir("/portaria", "JSESSIONID=sessao-que-expirou");

		assertThat(resposta.statusCode()).isEqualTo(302);
		assertThat(resposta.headers().firstValue("Location")).hasValueSatisfying(
				local -> assertThat(local).endsWith(SegurancaConfig.LOGIN_SESSAO_EXPIRADA));
		// Sessão nova no lugar da antiga: o próximo pedido não volta para cá
		String novoCookie = resposta.headers().allValues("Set-Cookie").stream()
				.filter(cookie -> cookie.startsWith("JSESSIONID=")).findFirst()
				.orElseThrow(() -> new AssertionError("Sem sessão nova no redirecionamento"))
				.split(";")[0];

		assertThat(pedir(SegurancaConfig.LOGIN_SESSAO_EXPIRADA, novoCookie).statusCode()).isEqualTo(200);
	}

	@Test
	void semCookieDeSessaoNaoHaAviso() throws Exception {
		HttpResponse<Void> resposta = pedir("/portaria", null);

		assertThat(resposta.statusCode()).isEqualTo(302);
		assertThat(resposta.headers().firstValue("Location")).hasValueSatisfying(
				local -> assertThat(local).endsWith("/login").doesNotContain("expirada"));
	}

	private HttpResponse<Void> pedir(String caminho, String cookie) throws Exception {
		HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + caminho));
		if (cookie != null) {
			pedido.header("Cookie", cookie);
		}
		return cliente.send(pedido.build(), HttpResponse.BodyHandlers.discarding());
	}

}
