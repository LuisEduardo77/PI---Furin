package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Spring Security base (T-09; RF03, RNF03, RNF04; arquitetura §6).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SegurancaTests {

	private static final String SENHA_DE_TESTE = "senha-de-teste-T09";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private PasswordEncoder encoder;

	// --- Negar por padrão (RF03) ---

	@Test
	void rotaNaoMapeadaSemLoginLevaAoLogin() throws Exception {
		mvc.perform(get("/rota-que-nao-existe"))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/portaria", "/alunos", "/presentes", "/historico", "/contas/equipe",
			"/senha/alterar", "/exemplo/layout" })
	void rotaDoFuncionarioSemLoginLevaAoLogin(String rota) throws Exception {
		mvc.perform(get(rota))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login"));
	}

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void rotaQueNaoExisteDa404AoFuncionario() throws Exception {
		// RF03, exceções; DEC-42. A rota com controller e sem regra (CA-RF03-04) continua
		// negada: está em AutorizacaoDeTodasAsRotasTests
		mvc.perform(get("/rota-que-nao-existe")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "OUTRO")
	void rotaQueNaoExisteEhNegadaParaQuemNaoEhFuncionario() throws Exception {
		mvc.perform(get("/rota-que-nao-existe")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void rotaComControllerSemRegraContinuaNegada() throws Exception {
		// CA-RF03-04: /error existe (Spring Boot), e nenhuma regra a libera a um pedido direto
		mvc.perform(get("/error")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void funcionarioAcessaRotaLiberada() throws Exception {
		mvc.perform(get("/exemplo/layout")).andExpect(status().isOk());
	}

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void rotaLiberadaAindaSemTelaDa404AoFuncionario() throws Exception {
		// RF03: rota inexistente dá 404, e não 403, dentro da área do funcionário
		mvc.perform(get("/historico")).andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser(roles = "OUTRO")
	void usuarioSemPerfilFuncionarioEhNegado() throws Exception {
		mvc.perform(get("/exemplo/layout")).andExpect(status().isForbidden());
	}

	@Test
	void loginEArquivosEstaticosSaoPublicos() throws Exception {
		mvc.perform(get("/login")).andExpect(status().isOk());
		mvc.perform(get("/css/base.css")).andExpect(status().isOk());
	}

	// --- CSRF ligado (A10 §8.28) ---

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void postSemTokenCsrfEhRecusado() throws Exception {
		mvc.perform(post("/logout")).andExpect(status().isForbidden());
		mvc.perform(post("/exemplo/layout").param("nome", "Ana Teste")).andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = SegurancaConfig.PERFIL_FUNCIONARIO)
	void sairComTokenCsrfVoltaAoLogin() throws Exception {
		mvc.perform(post("/logout").with(csrf()))
				.andExpect(status().isFound())
				.andExpect(redirectedUrl("/login?logout"));
	}

	// --- Cabeçalhos de segurança (arquitetura §6; RNF04) ---

	@Test
	void respostaTemCabecalhosDeSeguranca() throws Exception {
		mvc.perform(get("/login"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("X-Frame-Options", "DENY"))
				.andExpect(header().string("Referrer-Policy", "same-origin"))
				.andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
				.andExpect(header().string("Content-Security-Policy",
						org.hamcrest.Matchers.allOf(
								org.hamcrest.Matchers.containsString("default-src 'self'"),
								org.hamcrest.Matchers.containsString("frame-ancestors 'none'"))))
				// HSTS só com HTTPS (RNF04 planejado; o protótipo roda em HTTP)
				.andExpect(header().doesNotExist("Strict-Transport-Security"));
	}

	// --- PasswordEncoder delegante (RNF03) ---

	@Test
	void encoderGeraHashBcryptSemASenha() {
		String hash = encoder.encode(SENHA_DE_TESTE);
		assertThat(hash).startsWith("{bcrypt}$2").doesNotContain(SENHA_DE_TESTE);
		assertThat(encoder.matches(SENHA_DE_TESTE, hash)).isTrue();
		assertThat(encoder.matches("outra-senha", hash)).isFalse();
	}

	@Test
	void mesmaSenhaGeraHashesDiferentes() {
		// CA-RNF03-02: salt
		assertThat(encoder.encode(SENHA_DE_TESTE)).isNotEqualTo(encoder.encode(SENHA_DE_TESTE));
	}

}
