package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;

import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Tela da portaria fora do perfil {@code prototipo} (T-30; DEC-18, DEC-25; CA-RF09-08):
 * o campo de leitura e os painéis existem, mas nenhuma simulação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "sem.simulacao", roles = SegurancaConfig.PERFIL_FUNCIONARIO)
class PortariaSemSimulacaoTests {

	@Autowired
	private WebClient navegador;

	@BeforeEach
	void preparar() {
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
	}

	@Test
	void foraDoPrototipoNaoHaPainelDeSimulacaoNemBotaoDeGiro() throws Exception {
		HtmlPage portaria = navegador.getPage("http://localhost/portaria");

		assertThat(portaria.getElementById("campo-leitura")).isNotNull();
		assertThat(portaria.getElementById("painel-funcionario")).isNotNull();
		assertThat(portaria.getElementById("monitor-aluno")).isNotNull();
		assertThat(portaria.getElementById("titulo-simulacao")).isNull();
		assertThat(portaria.getElementById("botao-giro")).isNull();
		assertThat(portaria.querySelectorAll("[data-simular-leitura]")).isEmpty();
		assertThat(portaria.querySelector("main").getTextContent()).doesNotContain("protótipo");
	}

}
