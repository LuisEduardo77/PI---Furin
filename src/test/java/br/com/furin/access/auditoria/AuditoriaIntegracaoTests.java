package br.com.furin.access.auditoria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Log de auditoria com a aplicação de pé (T-10): ACESSO_NEGADO no 403 (RF03, CA-RF03-05)
 * e nenhuma senha nem e-mail completo no log depois de um login errado (CA-RNF03-04,
 * CA-RNF05-05).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuditoriaIntegracaoTests {

	private static final String SENHA_DE_TESTE = "senha-de-teste-T10-integracao";
	private static final String EMAIL = "carla.teste@example.com";

	@Autowired
	private MockMvc mvc;

	@Test
	@WithMockUser(username = EMAIL, roles = "OUTRO")
	void acessoNegadoVaiParaOLogComUsuarioMascarado(CapturedOutput saida) throws Exception {
		mvc.perform(get("/exemplo/layout?nome=Ana")).andExpect(status().isForbidden());

		assertThat(saida.getOut())
				.contains("evento=ACESSO_NEGADO", "usuario=c***@example.com", "metodo=GET",
						"rota=/exemplo/layout", "motivo=AUTORIZACAO")
				.doesNotContain(EMAIL, "nome=Ana");
	}

	@Test
	@WithMockUser(username = "ate.demo", roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void postSemCsrfVaiParaOLogComoAcessoNegado(CapturedOutput saida) throws Exception {
		mvc.perform(post("/exemplo/layout").param("nome", "Ana Teste")).andExpect(status().isForbidden());

		assertThat(saida.getOut())
				.contains("evento=ACESSO_NEGADO", "usuario=a***", "metodo=POST", "motivo=CSRF")
				.doesNotContain("Ana Teste");
	}

	@Test
	void loginErradoNaoDeixaSenhaNemEmailNoLog(CapturedOutput saida) throws Exception {
		mvc.perform(post("/login").with(csrf())
				.param("username", EMAIL)
				.param("password", SENHA_DE_TESTE))
				.andExpect(status().isFound());

		assertThat(saida.getAll()).doesNotContain(SENHA_DE_TESTE, EMAIL);
		Path arquivo = Path.of("logs", "auditoria.log");
		if (Files.exists(arquivo)) {
			assertThat(Files.readString(arquivo, StandardCharsets.UTF_8)).doesNotContain(SENHA_DE_TESTE, EMAIL);
		}
	}

	@Test
	void auditoriaTemArquivoProprio(CapturedOutput saida) throws Exception {
		mvc.perform(get("/exemplo/layout").with(
				org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
						.user("gestao.demo").roles("OUTRO")))
				.andExpect(status().isForbidden());

		Path arquivo = Path.of("logs", "auditoria.log");
		assertThat(arquivo).exists();
		assertThat(Files.readString(arquivo, StandardCharsets.UTF_8))
				.contains("evento=ACESSO_NEGADO", "usuario=g***", "rota=/exemplo/layout");
	}

}
