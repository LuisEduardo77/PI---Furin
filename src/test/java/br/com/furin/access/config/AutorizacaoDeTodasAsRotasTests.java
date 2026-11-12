package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import jakarta.persistence.EntityManager;

/**
 * Autorização de todas as rotas (T-17; RF03, CA-RF03-03 a 05; A10 §8.29).
 * <p>
 * As rotas <b>não</b> ficam numa lista: o teste lê todos os controllers do projeto no
 * {@link RequestMappingHandlerMapping} do Spring. Uma tela nova entra sozinha no teste,
 * que é rodado de novo no fim de cada fase do plano. Se ela não estiver em
 * {@code SegurancaConfig.ROTAS_DO_FUNCIONARIO}, o funcionário ativo recebe 403 e o teste
 * falha, avisando que faltou a regra.
 * <p>
 * Ficam de fora o {@code POST /login} e o {@code POST /logout}, que são do Spring
 * Security e não de um controller (testados em LoginTests e SessaoTests), e o
 * {@code /error} do Spring Boot.
 * <p>
 * Roda também com o perfil {@code prototipo}, onde existem todas as rotas, inclusive as
 * simuladas, como {@code POST /portaria/giros} (T-28). A carga fictícia desse perfil (V2)
 * fica desligada com {@code furin.carga.ativa=false}, para não gravar dados no banco de
 * testes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AutorizacaoDeTodasAsRotasTests.RotaDeExemploSemRegra.class)
class AutorizacaoDeTodasAsRotasTests {

	private static final String PACOTE_DO_PROJETO = "br.com.furin.access.";
	private static final String SENHA = "descartavel-ate-T17";

	/** Rota de exemplo com controller, mas sem regra em SegurancaConfig (CA-RF03-04). */
	static final String ROTA_SEM_REGRA = "/rota-de-exemplo-sem-regra";

	/** As três situações do CA-RF03-03. */
	enum Situacao {
		SEM_LOGIN, FUNCIONARIO_ATIVO, CONTA_DESATIVADA
	}

	/** Uma rota de um controller do projeto: método HTTP e caminho. */
	record Rota(HttpMethod metodo, String caminho) {

		@Override
		public String toString() {
			return metodo.name() + " " + caminho;
		}
	}

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeamentos;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private EntityManager jpa;

	@BeforeEach
	void preparar() {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, 0, NULL, ?)",
				"ATE Demo", "ate.demo", encoder.encode(SENHA), SegurancaConfig.PERFIL_FUNCIONARIO,
				LocalDateTime.now(ZoneOffset.UTC));
	}

	// --- Descoberta das rotas ---

	/** Todas as rotas dos controllers do projeto, menos a de exemplo deste teste. */
	List<Rota> rotasDoProjeto() {
		return mapeamentos.getHandlerMethods().entrySet().stream()
				.filter(mapeamento -> doProjeto(mapeamento.getValue()))
				.flatMap(mapeamento -> rotas(mapeamento.getKey()))
				.distinct()
				.sorted(Comparator.comparing(Rota::toString))
				.toList();
	}

	Stream<Arguments> rotasEmCadaSituacao() {
		return rotasDoProjeto().stream()
				.flatMap(rota -> Arrays.stream(Situacao.values()).map(situacao -> Arguments.of(rota, situacao)));
	}

	@Test
	void descobreAsRotasDosControllers() {
		// Se a descoberta quebrar e não achar nada, o teste parametrizado passaria vazio
		assertThat(rotasDoProjeto()).contains(
				new Rota(HttpMethod.GET, "/login"),
				new Rota(HttpMethod.GET, SegurancaConfig.TELA_INICIAL),
				new Rota(HttpMethod.POST, "/senha/alterar"),
				new Rota(HttpMethod.POST, "/sessao/renovar"),
				// Portaria antecipada (T-26 e T-28; o giro simulado só existe no prototipo)
				new Rota(HttpMethod.POST, "/portaria/leituras"),
				new Rota(HttpMethod.POST, "/portaria/giros"),
				// Expiração e cancelamento da liberação (T-29)
				new Rota(HttpMethod.GET, "/portaria/liberacoes/1"),
				new Rota(HttpMethod.POST, "/portaria/liberacoes/1/cancelar"),
				// Registro manual (T-32)
				new Rota(HttpMethod.GET, "/portaria/manual"),
				new Rota(HttpMethod.GET, "/portaria/manual/1"),
				new Rota(HttpMethod.POST, "/portaria/manual/1/revisar"),
				new Rota(HttpMethod.POST, "/portaria/manual/1/corrigir"),
				new Rota(HttpMethod.POST, "/portaria/manual/1"),
				// Área privada (T-18): a chave vira o valor qualquer da descoberta
				new Rota(HttpMethod.GET, "/arquivos/1"),
				// Cadastro de alunos (T-19)
				new Rota(HttpMethod.GET, "/alunos"),
				new Rota(HttpMethod.GET, "/alunos/novo"),
				new Rota(HttpMethod.POST, "/alunos"),
				// Revogação do termo (T-20)
				new Rota(HttpMethod.GET, "/alunos/1/consentimentos/1/revogacao"),
				new Rota(HttpMethod.POST, "/alunos/1/consentimentos/1/revogacao"),
				// Carteirinha (T-22)
				new Rota(HttpMethod.GET, "/carteirinhas/1/imprimir"),
				new Rota(HttpMethod.GET, "/carteirinhas/1/qr-code"),
				new Rota(HttpMethod.GET, "/alunos/1/carteirinhas/segunda-via"),
				new Rota(HttpMethod.POST, "/alunos/1/carteirinhas"),
				// Edição do aluno (T-23)
				new Rota(HttpMethod.GET, "/alunos/1/editar"),
				new Rota(HttpMethod.POST, "/alunos/1/editar/revisar"),
				new Rota(HttpMethod.POST, "/alunos/1/editar"),
				new Rota(HttpMethod.GET, "/alunos/1/inativacao"),
				new Rota(HttpMethod.POST, "/alunos/1/inativacao"),
				// Painel de presentes (T-34)
				new Rota(HttpMethod.GET, "/presentes"),
				// Histórico (T-35)
				new Rota(HttpMethod.GET, "/historico"),
				// Versão para impressão (T-36)
				new Rota(HttpMethod.GET, "/historico/impressao"),
				// Contas da equipe (T-43)
				new Rota(HttpMethod.GET, "/contas/equipe"),
				new Rota(HttpMethod.GET, "/contas/equipe/nova"),
				new Rota(HttpMethod.POST, "/contas/equipe"),
				new Rota(HttpMethod.GET, "/contas/equipe/senha-temporaria"),
				new Rota(HttpMethod.GET, "/contas/equipe/1/nova-senha"),
				new Rota(HttpMethod.POST, "/contas/equipe/1/nova-senha"),
				new Rota(HttpMethod.GET, "/contas/equipe/1/desativacao"),
				new Rota(HttpMethod.POST, "/contas/equipe/1/desativacao"),
				new Rota(HttpMethod.GET, "/contas/equipe/1/reativacao"),
				new Rota(HttpMethod.POST, "/contas/equipe/1/reativacao"))
				.doesNotContain(new Rota(HttpMethod.GET, ROTA_SEM_REGRA));
	}

	// --- CA-RF03-03: todas as rotas × três situações ---

	@ParameterizedTest(name = "{0} — {1}")
	@MethodSource("rotasEmCadaSituacao")
	void todaRotaRespeitaAAutorizacao(Rota rota, Situacao situacao) throws Exception {
		switch (situacao) {
			case SEM_LOGIN -> semLogin(rota);
			case FUNCIONARIO_ATIVO -> funcionarioAtivo(rota);
			case CONTA_DESATIVADA -> contaDesativada(rota);
		}
	}

	/** Sem login, leva ao login. A tela de login é a única rota pública de controller. */
	private void semLogin(Rota rota) throws Exception {
		MvcResult resultado = mvc.perform(pedido(rota)).andReturn();
		if (publica(rota)) {
			assertThat(resultado.getResponse().getStatus()).as(rota + " é pública").isEqualTo(200);
		} else {
			assertThat(resultado.getResponse().getRedirectedUrl()).as(rota + " sem login").isEqualTo("/login");
		}
	}

	/** FUNCIONARIO ativo acessa: nada de 403 nem de volta ao login. */
	private void funcionarioAtivo(Rota rota) throws Exception {
		MvcResult resultado = mvc.perform(pedido(rota).session(entrar())).andReturn();

		assertThat(resultado.getResponse().getStatus()).as(rota + " com funcionário ativo")
				.isNotIn(401, 403);
		String destino = resultado.getResponse().getRedirectedUrl();
		if (destino != null) {
			assertThat(destino).as(rota + " não pode voltar ao login").doesNotStartWith("/login");
		}
	}

	/** Conta desativada durante a sessão: a sessão é encerrada e o pedido vai para o login. */
	private void contaDesativada(Rota rota) throws Exception {
		MockHttpSession sessao = entrar();
		desativar("ate.demo");

		mvc.perform(pedido(rota).session(sessao)).andExpect(redirectedUrl(SegurancaConfig.LOGIN_CONTA_DESATIVADA));

		assertThat(sessao.isInvalid()).as("a sessão de " + rota + " foi encerrada").isTrue();
		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao)).andExpect(redirectedUrl("/login"));
	}

	// --- CA-RF03-04: rota nova sem regra explícita ---

	@Test
	void rotaComControllerMasSemRegraEhNegada() throws Exception {
		// O controller existe (RotaDeExemploSemRegra), mas a rota não está em ROTAS_DO_FUNCIONARIO
		mvc.perform(get(ROTA_SEM_REGRA).session(entrar())).andExpect(status().isForbidden());
	}

	@Test
	void rotaQueNaoExisteDa404AoFuncionario() throws Exception {
		// RF03, exceções; DEC-42: só a rota com controller é negada
		mvc.perform(get("/rota-que-nao-existe").session(entrar())).andExpect(status().isNotFound());
	}

	// --- CA-RF03-05: toda negação vai para o log ---

	@Test
	void negacaoVaiParaOLogComoAcessoNegadoSemDadosPessoais(CapturedOutput saida) throws Exception {
		mvc.perform(get(ROTA_SEM_REGRA).session(entrar())).andExpect(status().isForbidden());

		assertThat(linhasDeAcessoNegado(saida)).singleElement().asString()
				.contains("usuario=a***", "metodo=GET", "rota=" + ROTA_SEM_REGRA, "motivo=AUTORIZACAO")
				.doesNotContain("ate.demo");
	}

	@Test
	void contaDesativadaVaiParaOLogComoAcessoNegado(CapturedOutput saida) throws Exception {
		MockHttpSession sessao = entrar();
		desativar("ate.demo");

		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao))
				.andExpect(redirectedUrl(SegurancaConfig.LOGIN_CONTA_DESATIVADA));

		assertThat(linhasDeAcessoNegado(saida)).singleElement().asString()
				.contains("usuario=a***", "rota=" + SegurancaConfig.TELA_INICIAL, "motivo=CONTA_DESATIVADA")
				.doesNotContain("ate.demo");
	}

	@Test
	void loginMostraOAvisoDeContaDesativada() throws Exception {
		// RF03, exceções: texto [PROP] do RF03, em role="alert"
		mvc.perform(get(SegurancaConfig.LOGIN_CONTA_DESATIVADA))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Sua conta foi desativada. Procure a equipe da escola.")))
				.andExpect(content().string(containsString("id=\"aviso-desativada\" role=\"alert\"")));
	}

	@Test
	void contaDesativadaNaoEntraDeNovo() throws Exception {
		MockHttpSession sessao = entrar();
		desativar("ate.demo");
		mvc.perform(get(SegurancaConfig.TELA_INICIAL).session(sessao));

		mvc.perform(formLogin().user("ate.demo").password(SENHA)).andExpect(redirectedUrl("/login"));
	}

	// --- Apoio ---

	private MockHttpServletRequestBuilder pedido(Rota rota) {
		MockHttpServletRequestBuilder pedido = request(rota.metodo(), rota.caminho());
		// O token CSRF vai em cada pedido que altera algo: aqui se testa a autorização
		return rota.metodo() == HttpMethod.GET ? pedido : pedido.with(csrf());
	}

	private MockHttpSession entrar() throws Exception {
		return (MockHttpSession) mvc.perform(formLogin().user("ate.demo").password(SENHA))
				.andExpect(authenticated())
				.andReturn().getRequest().getSession(false);
	}

	/**
	 * Desativa a conta direto no banco, como faria outra pessoa em outra sessão (RF26). O
	 * cache do JPA é limpo porque o teste inteiro roda numa transação só; na aplicação,
	 * cada pedido tem a sua.
	 */
	private void desativar(String login) {
		jdbc.update("UPDATE usuario SET ativo = 0 WHERE login = ?", login);
		jpa.clear();
	}

	private static boolean publica(Rota rota) {
		return Set.of(SegurancaConfig.ROTAS_PUBLICAS).contains(rota.caminho());
	}

	private static boolean doProjeto(HandlerMethod metodo) {
		Class<?> controller = metodo.getBeanType();
		return controller.getName().startsWith(PACOTE_DO_PROJETO) && controller != RotaDeExemploSemRegra.class;
	}

	/** Cada caminho com cada método HTTP. Sem método declarado, vale o GET. */
	private static Stream<Rota> rotas(RequestMappingInfo info) {
		Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
		List<HttpMethod> http = metodos.isEmpty() ? List.of(HttpMethod.GET)
				: metodos.stream().map(metodo -> HttpMethod.valueOf(metodo.name())).toList();
		return info.getPatternValues().stream()
				// Variável no caminho ({id}) vira um valor qualquer
				.map(caminho -> caminho.replaceAll("\\{[^}]+}", "1"))
				.flatMap(caminho -> http.stream().map(metodo -> new Rota(metodo, caminho)));
	}

	private static List<String> linhasDeAcessoNegado(CapturedOutput saida) {
		return saida.getOut().lines().filter(linha -> linha.contains("evento=ACESSO_NEGADO")).toList();
	}

	/** Controller só deste teste: existe, mas nenhuma regra libera a rota (CA-RF03-04). */
	@RestController
	static class RotaDeExemploSemRegra {

		@GetMapping(ROTA_SEM_REGRA)
		String exemplo() {
			return "não deveria aparecer";
		}

	}

}
