package br.com.furin.access.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.autenticacao.TentativasDeLogin;
import br.com.furin.access.autenticacao.TrocaDeSenhaObrigatoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Contas da equipe (T-43; RF26; RN14; DEC-39): CA-RF26-01 a 05 e 07 a 09. O CA-RF26-06
 * (autoria preservada) está também em HistoricoTests. Os valores vêm de
 * {@code furin.senha.*}, e o tempo passa pelo relógio do teste. Só dados fictícios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class ContasDaEquipeTests {

	private static final String SENHA_GESTAO = "descartavel-gestao-T43";
	private static final String SENHA_ATE = "descartavel-ate-T43";
	private static final String SENHA_PAULA = "senha-antiga-paula-T43";

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(Instant.parse("2026-11-11T13:00:00Z"));
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private WebClient navegador;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private TentativasDeLogin tentativas;

	@Autowired
	private FurinProperties propriedades;

	private long idGestao;
	private long idAte;
	private long idPaula;

	@BeforeEach
	void preparar() {
		idGestao = conta("Gestão Demo", "gestao.demo", SENHA_GESTAO, true);
		idAte = conta("ATE Demo", "ate.demo", SENHA_ATE, true);
		idPaula = conta("Paula Exemplo", "secretaria.paula", SENHA_PAULA, true);
		tentativas.esquecerTodos();
		navegador.getOptions().setCssEnabled(false);
		navegador.getOptions().setJavaScriptEnabled(false);
		navegador.getCookieManager().clearCookies();
	}

	// --- Listar ---

	@Test
	void listaMostraNomeLoginSituacaoEDataDeCriacao() throws Exception {
		jdbc.update("UPDATE usuario SET ativo = 0 WHERE id_usuario = ?", idPaula);

		Pagina pagina = pagina(get("/contas/equipe").with(como("gestao.demo")));

		assertThat(pagina.titulo()).isEqualTo("Contas da equipe | Furin Access");
		assertThat(pagina.texto("nav a[aria-current='page']")).isEqualTo("Contas da equipe");
		assertThat(pagina.textos("#tabela-contas thead th"))
				.containsExactly("Nome", "Login", "Situação", "Criada em", "Ações");
		// 11/11/2026 13:00 UTC = 10:00 em America/Sao_Paulo (RN10)
		assertThat(pagina.textos("#conta-" + idPaula + " > *")).startsWith("Paula Exemplo", "secretaria.paula",
				"Inativa", "11/11/2026");
		assertThat(pagina.texto("#conta-" + idAte + " > td:nth-of-type(2)")).isEqualTo("Ativa");
	}

	// --- CA-RF26-01, 02 e 03: criar ---

	@Test
	void gestaoCriaContaFuncionarioComTrocaObrigatoriaEPrazoDaValidade() throws Exception {
		String senha = criar("gestao.demo", "Bruno Exemplo", "  Secretaria.Bruno  ");

		Map<String, Object> linha = linha("secretaria.bruno");
		assertThat(linha).containsEntry("nome", "Bruno Exemplo")
				.containsEntry("perfil", SegurancaConfig.PERFIL_FUNCIONARIO)
				.containsEntry("ativo", true)
				.containsEntry("troca_senha_obrigatoria", true);
		LocalDateTime prazo = LocalDateTime.ofInstant(
				relogio.instant().plus(propriedades.senha().validadeTemporaria()), ZoneOffset.UTC);
		assertThat(linha).containsEntry("senha_temporaria_expira_em", prazo);

		// RF01: no primeiro acesso, cai na troca de senha
		mvc.perform(formLogin().user("secretaria.bruno").password(senha))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA))
				.andExpect(authenticated().withUsername("secretaria.bruno"));
	}

	@Test
	void senhaTemporariaTemOTamanhoConfiguradoEFicaSoComoHashForaDoLog(CapturedOutput saida) throws Exception {
		String senha = criar("gestao.demo", "Bruno Exemplo", "secretaria.bruno");

		assertThat(senha).hasSize(propriedades.senha().tamanhoTemporaria())
				.matches("[" + GeradorDeSenhaTemporaria.ALFABETO + "]+");
		String hash = (String) linha("secretaria.bruno").get("senha_hash");
		assertThat(hash).startsWith("{bcrypt}").doesNotContain(senha);
		assertThat(encoder.matches(senha, hash)).isTrue();
		assertThat(saida.getOut()).contains("evento=CONTA_CRIADA", "usuario=g***",
				"idConta=" + linha("secretaria.bruno").get("id_usuario"));
		assertThat(saida.getAll()).doesNotContain(senha);
	}

	@Test
	void cadaContaRecebeUmaSenhaDiferente() throws Exception {
		assertThat(criar("gestao.demo", "Bruno Exemplo", "secretaria.bruno"))
				.isNotEqualTo(criar("gestao.demo", "Carla Exemplo", "secretaria.carla"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "GESTAO", "ADMIN", "ATE" })
	void perfilEnviadoPelaInterfaceEhIgnorado(String perfil) throws Exception {
		mvc.perform(post("/contas/equipe").with(como("gestao.demo")).with(csrf())
				.param("nome", "Bruno Exemplo").param("login", "secretaria.bruno").param("perfil", perfil))
				.andExpect(redirectedUrl(ContasDaEquipeController.PAGINA_DA_SENHA));

		assertThat(linha("secretaria.bruno")).containsEntry("perfil", SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	@Test
	void nomeELoginVaziosSaoRecusadosComOsCamposMarcados() throws Exception {
		Pagina pagina = pagina(post("/contas/equipe").with(como("gestao.demo")).with(csrf())
				.param("nome", "  ").param("login", ""));

		assertThat(pagina.titulo()).isEqualTo("Nova conta | Furin Access");
		assertThat(pagina.texto("#nome-erro")).contains("Informe o nome.");
		assertThat(pagina.texto("#login-erro")).contains("Informe o login.");
		assertThat(pagina.atributo("#nome", "aria-invalid")).isEqualTo("true");
		assertThat(pagina.atributo("#nome", "aria-describedby")).isEqualTo("nome-erro");
		assertThat(pagina.atributo("#login", "aria-describedby")).isEqualTo("login-erro login-dica");
		assertThat(pagina.textos("#resumo-erros a")).containsExactly("Informe o nome.", "Informe o login.");
		assertThat(pagina.todos("label[for='nome']")).hasSize(1);
		assertThat(pagina.todos("label[for='login']")).hasSize(1);
	}

	@Test
	void loginRepetidoEhRecusadoMesmoComOutraCaixaEOsDadosFicam() throws Exception {
		Pagina pagina = pagina(post("/contas/equipe").with(como("gestao.demo")).with(csrf())
				.param("nome", "Outra Paula").param("login", " SECRETARIA.Paula "));

		assertThat(pagina.texto("#login-erro")).contains("Este login já está em uso. Escolha outro.");
		assertThat(pagina.atributo("#login", "aria-invalid")).isEqualTo("true");
		assertThat(pagina.atributo("#nome", "value")).isEqualTo("Outra Paula");
		assertThat(pagina.atributo("#login", "value")).isEqualTo("SECRETARIA.Paula");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuario WHERE nome = 'Outra Paula'", Integer.class))
				.isZero();
	}

	// --- CA-RF26-04: ate.demo também cria, desativa e reativa ---

	@Test
	void ateCriaDesativaEReativaComOsEventosNoLog(CapturedOutput saida) throws Exception {
		String primeira = criar("ate.demo", "Bruno Exemplo", "secretaria.bruno");
		long idBruno = ((Number) linha("secretaria.bruno").get("id_usuario")).longValue();

		mvc.perform(post("/contas/equipe/{id}/desativacao", idBruno).with(como("ate.demo")).with(csrf()))
				.andExpect(redirectedUrl(ContasDaEquipeController.LISTA))
				.andExpect(result -> {
					Map<String, Object> flash = result.getFlashMap();
					assertThat(flash).containsEntry("contaDesativada", "Bruno Exemplo");
				});
		assertThat(linha("secretaria.bruno")).containsEntry("ativo", false);

		String reativada = senhaDaPagina(mvc.perform(post("/contas/equipe/{id}/reativacao", idBruno)
				.with(como("ate.demo")).with(csrf()))
				.andExpect(redirectedUrl(ContasDaEquipeController.PAGINA_DA_SENHA)).andReturn());

		// RF26: reativar exige uma nova senha temporária [PROP]; a anterior não vale mais
		assertThat(linha("secretaria.bruno")).containsEntry("ativo", true).containsEntry("troca_senha_obrigatoria", true);
		assertThat(reativada).isNotEqualTo(primeira);
		mvc.perform(formLogin().user("secretaria.bruno").password(primeira)).andExpect(unauthenticated());
		mvc.perform(formLogin().user("secretaria.bruno").password(reativada))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));

		for (String evento : List.of("CONTA_CRIADA", "CONTA_DESATIVADA", "CONTA_REATIVADA", "SENHA_TEMPORARIA_GERADA")) {
			assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=" + evento + " ")))
					.as(evento).singleElement().asString().contains("usuario=a***", "idConta=" + idBruno);
		}
		assertThat(saida.getAll()).doesNotContain(primeira).doesNotContain(reativada);
	}

	@Test
	void novaSenhaTemporariaInvalidaAAnteriorERecomecaOPrazo(CapturedOutput saida) throws Exception {
		jdbc.update("UPDATE usuario SET troca_senha_obrigatoria = 0, senha_temporaria_expira_em = NULL "
				+ "WHERE id_usuario = ?", idPaula);

		String nova = senhaDaPagina(mvc.perform(post("/contas/equipe/{id}/nova-senha", idPaula)
				.with(como("gestao.demo")).with(csrf()))
				.andExpect(redirectedUrl(ContasDaEquipeController.PAGINA_DA_SENHA)).andReturn());

		assertThat(linha("secretaria.paula")).containsEntry("troca_senha_obrigatoria", true)
				.containsEntry("senha_temporaria_expira_em", LocalDateTime.ofInstant(
						relogio.instant().plus(propriedades.senha().validadeTemporaria()), ZoneOffset.UTC));
		mvc.perform(formLogin().user("secretaria.paula").password(SENHA_PAULA)).andExpect(unauthenticated());
		mvc.perform(formLogin().user("secretaria.paula").password(nova))
				.andExpect(redirectedUrl(TrocaDeSenhaObrigatoria.TELA_DE_TROCA));
		assertThat(saida.getOut()).contains("evento=SENHA_TEMPORARIA_GERADA", "usuario=g***", "idConta=" + idPaula);
		assertThat(saida.getAll()).doesNotContain(nova);
	}

	@Test
	void contaInativaNaoRecebeNovaSenhaSemReativar() throws Exception {
		jdbc.update("UPDATE usuario SET ativo = 0 WHERE id_usuario = ?", idPaula);
		String hashAntes = (String) linha("secretaria.paula").get("senha_hash");

		Pagina pagina = pagina(post("/contas/equipe/{id}/nova-senha", idPaula).with(como("gestao.demo")).with(csrf()));

		assertThat(pagina.texto("#erro-acao")).contains("Reativar");
		assertThat(linha("secretaria.paula")).containsEntry("senha_hash", hashAntes);
	}

	// --- CA-RF26-05: ninguém desativa a própria conta ---

	@Test
	void ninguemDesativaAPropriaConta(CapturedOutput saida) throws Exception {
		for (String login : List.of("gestao.demo", "ate.demo")) {
			long id = login.equals("gestao.demo") ? idGestao : idAte;
			Pagina aoEnviar = pagina(post("/contas/equipe/{id}/desativacao", id).with(como(login)).with(csrf()));
			Pagina aoAbrir = pagina(get("/contas/equipe/{id}/desativacao", id).with(como(login)));

			for (Pagina pagina : List.of(aoEnviar, aoAbrir)) {
				assertThat(pagina.texto("#erro-acao")).isEqualTo("Você não pode desativar a sua própria conta.");
				assertThat(pagina.todos("main form[method=post]")).isEmpty();
			}
			assertThat(linha(login)).containsEntry("ativo", true);
		}
		assertThat(saida.getOut()).doesNotContain("evento=CONTA_DESATIVADA");
	}

	@Test
	void naListaAPropriaContaNaoTemODesativarEMostraOMotivo() throws Exception {
		Pagina pagina = pagina(get("/contas/equipe").with(como("gestao.demo")));

		assertThat(pagina.textos("#conta-" + idGestao + " a")).containsExactly(
				"Gerar nova senha temporária para Gestão Demo");
		assertThat(pagina.texto("#conta-" + idGestao + " ul"))
				.contains("Você não pode desativar a sua própria conta.");
		assertThat(pagina.textos("#conta-" + idAte + " a")).contains("Desativar ATE Demo");
	}

	// --- CA-RF26-06: conta desativada não entra (a autoria fica em HistoricoTests) ---

	@Test
	void contaDesativadaNaoEntra() throws Exception {
		mvc.perform(formLogin().user("secretaria.paula").password(SENHA_PAULA)).andExpect(authenticated());

		mvc.perform(post("/contas/equipe/{id}/desativacao", idPaula).with(como("gestao.demo")).with(csrf()))
				.andExpect(redirectedUrl(ContasDaEquipeController.LISTA));

		mvc.perform(formLogin().user("secretaria.paula").password(SENHA_PAULA))
				.andExpect(redirectedUrl("/login"))
				.andExpect(unauthenticated());
	}

	// --- CA-RF26-07: sem login e ID inexistente ---

	@ParameterizedTest
	@ValueSource(strings = { "/contas/equipe", "/contas/equipe/nova", "/contas/equipe/senha-temporaria",
			"/contas/equipe/1/nova-senha", "/contas/equipe/1/desativacao", "/contas/equipe/1/reativacao" })
	void semLoginLevaAoLogin(String rota) throws Exception {
		mvc.perform(get(rota)).andExpect(redirectedUrl("/login"));
		mvc.perform(post(rota).with(csrf())).andExpect(redirectedUrl("/login"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "nova-senha", "desativacao", "reativacao" })
	void idInexistenteDa404EVaiParaOLog(String acao, CapturedOutput saida) throws Exception {
		long inexistente = idPaula + 1000;

		mvc.perform(get("/contas/equipe/{id}/" + acao, inexistente).with(como("gestao.demo")))
				.andExpect(status().isNotFound());
		mvc.perform(post("/contas/equipe/{id}/" + acao, inexistente).with(como("gestao.demo")).with(csrf()))
				.andExpect(status().isNotFound());

		assertThat(saida.getOut()).contains("evento=CONTA_NAO_ENCONTRADA", "usuario=g***", "idConta=" + inexistente);
	}

	// --- CA-RF26-08: confirmação e nomes acessíveis ---

	@Test
	void acoesDaListaTemONomeDaPessoaELevamAConfirmacao() throws Exception {
		jdbc.update("UPDATE usuario SET ativo = 0 WHERE id_usuario = ?", idAte);
		Pagina lista = pagina(get("/contas/equipe").with(como("gestao.demo")));

		assertThat(lista.textos("#conta-" + idPaula + " a")).containsExactly(
				"Gerar nova senha temporária para Paula Exemplo", "Desativar Paula Exemplo");
		assertThat(lista.textos("#conta-" + idAte + " a")).containsExactly("Reativar ATE Demo");
		// Links e botões nativos, sem tabindex negativo: tudo funciona por teclado
		assertThat(lista.todos("main [tabindex='-1']")).isEmpty();
		// A lista não altera nada: cada ação passa pela página de confirmação
		assertThat(lista.todos("main form[method=post]")).isEmpty();
		assertThat(lista.atributos("#conta-" + idPaula + " a", "href")).containsExactly(
				"/contas/equipe/" + idPaula + "/nova-senha", "/contas/equipe/" + idPaula + "/desativacao");
	}

	@Test
	void cadaPaginaDeConfirmacaoTemTituloBotaoComONomeECancelar() throws Exception {
		jdbc.update("UPDATE usuario SET ativo = 0 WHERE id_usuario = ?", idAte);
		record Esperado(String rota, String titulo, String botao) {
		}
		for (Esperado esperado : List.of(
				new Esperado("/contas/equipe/" + idPaula + "/nova-senha", "Gerar nova senha temporária para Paula Exemplo",
						"Gerar a nova senha de Paula Exemplo"),
				new Esperado("/contas/equipe/" + idPaula + "/desativacao", "Desativar Paula Exemplo",
						"Desativar a conta de Paula Exemplo"),
				new Esperado("/contas/equipe/" + idAte + "/reativacao", "Reativar ATE Demo",
						"Reativar a conta de ATE Demo"))) {
			Pagina pagina = pagina(get(esperado.rota()).with(como("gestao.demo")));

			assertThat(pagina.titulo()).isEqualTo(esperado.titulo() + " | Furin Access");
			assertThat(pagina.texto("h1")).isEqualTo(esperado.titulo());
			assertThat(pagina.atributo("main form", "action")).isEqualTo(esperado.rota());
			assertThat(pagina.atributo("main form", "method")).isEqualTo("post");
			assertThat(pagina.textos("main form button")).containsExactly(esperado.botao());
			assertThat(pagina.textos("main form a")).containsExactly("Cancelar");
		}
		// Abrir a confirmação não muda nada
		assertThat(linha("secretaria.paula")).containsEntry("ativo", true);
		assertThat(linha("ate.demo")).containsEntry("ativo", false);
	}

	// --- CA-RF26-09: senha exibida uma vez, sem cache ---

	@Test
	void senhaApareceUmaVezERecarregarNaoMostraDeNovo() throws Exception {
		HtmlPage nova = entrarPeloFormulario("gestao.demo", SENHA_GESTAO, "/contas/equipe/nova");
		((HtmlInput) nova.getElementById("nome")).setValue("Bruno Exemplo");
		((HtmlInput) nova.getElementById("login")).setValue("secretaria.bruno");
		HtmlPage senha = ((HtmlButton) nova.querySelector("main form button[type=submit]")).click();

		assertThat(senha.getUrl().getPath()).isEqualTo(ContasDaEquipeController.PAGINA_DA_SENHA);
		assertThat(senha.getTitleText()).isEqualTo("Senha temporária | Furin Access");
		String exibida = senha.getElementById("senha-temporaria").getTextContent();
		assertThat(encoder.matches(exibida, (String) linha("secretaria.bruno").get("senha_hash"))).isTrue();
		assertThat(senha.querySelector("output#aviso-senha-gerada").getTextContent()).contains("Sucesso:",
				"conta criada para Bruno Exemplo.");
		assertThat(senha.asNormalizedText()).contains("72 horas", "não será exibida novamente");
		assertThat(senha.getWebResponse().getResponseHeaderValue("Cache-Control")).contains("no-store");

		HtmlPage recarregada = (HtmlPage) senha.refresh();
		assertThat(recarregada.getElementById("senha-temporaria")).isNull();
		assertThat(recarregada.getWebResponse().getContentAsString()).doesNotContain(exibida);
		assertThat(recarregada.getElementById("senha-ja-exibida").getTextContent())
				.contains("já foi exibida e não aparece de novo");
	}

	@Test
	void paginaDaSenhaTemNoStoreMesmoSemSenha() throws Exception {
		MockHttpServletResponse resposta = mvc.perform(get(ContasDaEquipeController.PAGINA_DA_SENHA)
				.with(como("gestao.demo")))
				.andExpect(status().isOk())
				.andExpect(header().string("Cache-Control", containsString("no-store")))
				.andReturn().getResponse();

		assertThat(resposta.getContentAsString()).contains("id=\"senha-ja-exibida\"")
				.doesNotContain("id=\"senha-temporaria\"");
	}

	@Test
	void aSenhaNaoFicaNoPostNemNaUrl() throws Exception {
		MvcResult criacao = mvc.perform(post("/contas/equipe").with(como("gestao.demo")).with(csrf())
				.param("nome", "Bruno Exemplo").param("login", "secretaria.bruno"))
				.andExpect(redirectedUrl(ContasDaEquipeController.PAGINA_DA_SENHA)).andReturn();
		String senha = senhaDaPagina(criacao);

		// O POST só redireciona: voltar para ele não reenvia nem mostra a senha
		assertThat(criacao.getResponse().getContentAsString()).isEmpty();
		assertThat(criacao.getResponse().getRedirectedUrl()).doesNotContain(senha);
	}

	// --- Apoio ---

	/** Cria pela tela e devolve a senha exibida, lida como na página (atributo flash). */
	private String criar(String autor, String nome, String login) throws Exception {
		return senhaDaPagina(mvc.perform(post("/contas/equipe").with(como(autor)).with(csrf())
				.param("nome", nome).param("login", login))
				.andExpect(redirectedUrl(ContasDaEquipeController.PAGINA_DA_SENHA)).andReturn());
	}

	/** Segue o redirecionamento com o atributo flash e lê a senha da página. */
	private String senhaDaPagina(MvcResult acao) throws Exception {
		Pagina pagina = pagina(get(ContasDaEquipeController.PAGINA_DA_SENHA).with(como("gestao.demo"))
				.flashAttrs(acao.getFlashMap()));
		String senha = pagina.texto("#senha-temporaria");
		assertThat(senha).as("senha exibida").isNotBlank();
		return senha;
	}

	private HtmlPage entrarPeloFormulario(String login, String senha, String destino) throws Exception {
		HtmlPage pagina = navegador.getPage("http://localhost/login");
		((HtmlInput) pagina.getElementById("usuario")).setValue(login);
		((HtmlInput) pagina.getElementById("senha")).setValue(senha);
		((HtmlButton) pagina.querySelector("main form button[type=submit]")).click();
		return navegador.getPage("http://localhost" + destino);
	}

	private Pagina pagina(MockHttpServletRequestBuilder pedido) throws Exception {
		return new Pagina(mvc.perform(pedido).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString());
	}

	private static RequestPostProcessor como(String login) {
		return user(login).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private Map<String, Object> linha(String login) {
		return jdbc.queryForMap("SELECT id_usuario, nome, perfil, ativo, senha_hash, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em FROM usuario WHERE login = ?", login);
	}

	private long conta(String nome, String login, String senha, boolean ativo) {
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, ?, 0, NULL, ?)",
				nome, login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, ativo,
				LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC));
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	/** A resposta lida pelo parser do HtmlUnit, sem JavaScript, com seletores CSS. */
	private static final class Pagina {

		private final HtmlPage pagina;

		Pagina(String html) throws Exception {
			// O cliente fica aberto: fechá-lo desmontaria a página antes das consultas
			WebClient cliente = new WebClient();
			cliente.getOptions().setJavaScriptEnabled(false);
			cliente.getOptions().setCssEnabled(false);
			pagina = cliente.loadHtmlCodeIntoCurrentWindow(html);
		}

		String titulo() {
			return pagina.getTitleText();
		}

		List<DomElement> todos(String css) {
			List<DomElement> elementos = new ArrayList<>();
			for (DomNode no : pagina.querySelectorAll(css)) {
				elementos.add((DomElement) no);
			}
			return elementos;
		}

		String texto(String css) {
			DomNode no = pagina.querySelector(css);
			return no == null ? null : limpo(no.getTextContent());
		}

		List<String> textos(String css) {
			return todos(css).stream().map(elemento -> limpo(elemento.getTextContent())).toList();
		}

		String atributo(String css, String nome) {
			DomElement elemento = pagina.querySelector(css);
			return elemento == null || !elemento.hasAttribute(nome) ? "" : elemento.getAttribute(nome);
		}

		List<String> atributos(String css, String nome) {
			return todos(css).stream().map(elemento -> elemento.getAttribute(nome)).toList();
		}

		private static String limpo(String texto) {
			return texto.replaceAll("\\s+", " ").strip();
		}
	}

}
