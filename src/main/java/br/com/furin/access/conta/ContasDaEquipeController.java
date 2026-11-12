package br.com.furin.access.conta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.autenticacao.Usuario;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.conta.ContasDaEquipe.LoginEmUso;
import br.com.furin.access.conta.ContasDaEquipe.PropriaConta;
import br.com.furin.access.conta.ContasDaEquipe.SenhaGerada;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Tela "Contas da equipe" (T-43; RF26; RN14; DEC-39):
 * <ul>
 * <li>{@code GET /contas/equipe}: lista, com as ações de cada conta;</li>
 * <li>{@code GET /contas/equipe/nova} e {@code POST /contas/equipe}: "Nova conta";</li>
 * <li>{@code GET/POST /contas/equipe/{id}/nova-senha}, {@code …/desativacao} e
 * {@code …/reativacao}: cada ação com a sua página de confirmação (A11 §5.9.12);</li>
 * <li>{@code GET /contas/equipe/senha-temporaria}: a senha gerada, uma única vez.</li>
 * </ul>
 * A senha temporária passa do POST para a página da senha num atributo <i>flash</i>, que o
 * Spring apaga da sessão na primeira leitura: recarregar ou voltar para a página não a
 * mostra de novo (CA-RF26-09). A página da senha sai com {@code Cache-Control: no-store}
 * (DEC-28, E7). O perfil não é um campo do formulário: qualquer valor enviado é ignorado.
 */
@Controller
public class ContasDaEquipeController {

	static final String LISTA = "/contas/equipe";
	static final String PAGINA_DA_SENHA = LISTA + "/senha-temporaria";

	private static final String PARA_A_LISTA = "redirect:" + LISTA;
	private static final String PARA_A_SENHA = "redirect:" + PAGINA_DA_SENHA;
	private static final String TELA_LISTA = "contas/lista";
	private static final String TELA_NOVA = "contas/nova";
	private static final String TELA_CONFIRMAR = "contas/confirmar";
	private static final String TELA_SENHA = "contas/senha-temporaria";
	private static final String SENHA_GERADA = "senhaGerada";
	private static final String CONTA = "conta";

	/** RF26, exceções [PROP: texto]. */
	static final String PROPRIA_CONTA = "Você não pode desativar a sua própria conta.";
	static final String LOGIN_EM_USO = "Este login já está em uso. Escolha outro.";

	private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final ContasDaEquipe contas;

	private final ZoneId fuso;

	private final long horasDeValidade;

	public ContasDaEquipeController(ContasDaEquipe contas, FurinProperties propriedades) {
		this.contas = contas;
		this.fuso = propriedades.escola().fuso();
		this.horasDeValidade = propriedades.senha().validadeTemporaria().toHours();
	}

	/** Uma linha da lista (RF26, "Listar", passo 2). Data de criação no fuso da escola (RN10). */
	public record LinhaDaConta(long id, String nome, String login, boolean ativa, String criadaEm, boolean propria) {
	}

	/** As três ações com confirmação (RF26, "Gerar nova senha, desativar e reativar"). Textos [PROP]. */
	public enum Acao {
		NOVA_SENHA("nova-senha", "Gerar nova senha temporária para %s",
				"A senha atual de %s deixa de valer. A nova senha temporária aparece uma única vez.",
				"Gerar a nova senha"),
		DESATIVACAO("desativacao", "Desativar %s",
				"%s não vai mais conseguir entrar no sistema. Os registros feitos por essa conta continuam "
						+ "mostrando o nome de quem os fez.",
				"Desativar a conta"),
		REATIVACAO("reativacao", "Reativar %s",
				"A conta de %s volta a entrar no sistema com uma nova senha temporária, que aparece uma única vez.",
				"Reativar a conta");

		private final String caminho;
		private final String titulo;
		private final String explicacao;
		private final String botao;

		Acao(String caminho, String titulo, String explicacao, String botao) {
			this.caminho = caminho;
			this.titulo = titulo;
			this.explicacao = explicacao;
			this.botao = botao;
		}

		public String getCaminho() {
			return caminho;
		}

		public String getBotao() {
			return botao;
		}

		public String titulo(String nome) {
			return titulo.formatted(nome);
		}

		public String explicacao(String nome) {
			return explicacao.formatted(nome);
		}
	}

	// --- Listar ---

	@GetMapping(LISTA)
	public String listar(Authentication autenticacao, Model model) {
		List<LinhaDaConta> linhas = contas.todas().stream().map(conta -> linha(conta, autenticacao)).toList();
		model.addAttribute("contas", linhas);
		return TELA_LISTA;
	}

	// --- Criar ---

	@GetMapping(LISTA + "/nova")
	public String exibirNova(Model model) {
		model.addAttribute(CONTA, new NovaContaForm());
		return TELA_NOVA;
	}

	@PostMapping(LISTA)
	public String criar(@Valid @ModelAttribute(CONTA) NovaContaForm form, BindingResult erros,
			RedirectAttributes redirecionamento) {
		if (erros.hasErrors()) {
			return TELA_NOVA;
		}
		try {
			redirecionamento.addFlashAttribute(SENHA_GERADA, contas.criar(form.getNome(), form.getLogin()));
			return PARA_A_SENHA;
		} catch (LoginEmUso | DataIntegrityViolationException emUso) {
			// DataIntegrityViolation: duas criações com o mesmo login ao mesmo tempo (uk_usuario_login)
			erros.rejectValue("login", "emUso", LOGIN_EM_USO);
			return TELA_NOVA;
		}
	}

	// --- Senha exibida uma vez (CA-RF26-02, CA-RF26-09) ---

	@GetMapping(PAGINA_DA_SENHA)
	public String exibirSenha(Model model, HttpServletResponse resposta) {
		// DEC-28, E7. O Spring Security já manda no-store; aqui fica explícito, só para esta página
		resposta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		resposta.setHeader(HttpHeaders.PRAGMA, "no-cache");
		model.addAttribute("horasDeValidade", horasDeValidade);
		// Sem o atributo flash (recarregar, voltar), a página só diz que a senha já foi exibida
		return TELA_SENHA;
	}

	// --- Ações com confirmação ---

	@GetMapping(LISTA + "/{idConta}/nova-senha")
	public String confirmarNovaSenha(@PathVariable long idConta, Authentication autenticacao, Model model) {
		return confirmacao(Acao.NOVA_SENHA, idConta, autenticacao, model);
	}

	@PostMapping(LISTA + "/{idConta}/nova-senha")
	public String gerarNovaSenha(@PathVariable long idConta, Authentication autenticacao, Model model,
			RedirectAttributes redirecionamento) {
		if (!contas.conta(idConta).isAtivo()) {
			// Conta inativa volta pela reativação, que já gera a senha
			return confirmacao(Acao.NOVA_SENHA, idConta, autenticacao, model);
		}
		redirecionamento.addFlashAttribute(SENHA_GERADA, contas.gerarNovaSenha(idConta));
		return PARA_A_SENHA;
	}

	@GetMapping(LISTA + "/{idConta}/desativacao")
	public String confirmarDesativacao(@PathVariable long idConta, Authentication autenticacao, Model model) {
		return confirmacao(Acao.DESATIVACAO, idConta, autenticacao, model);
	}

	@PostMapping(LISTA + "/{idConta}/desativacao")
	public String desativar(@PathVariable long idConta, Authentication autenticacao, Model model,
			RedirectAttributes redirecionamento) {
		try {
			contas.desativar(idConta, autenticacao.getName());
		} catch (PropriaConta propria) {
			// CA-RF26-05: nada muda, e a página explica por quê
			return confirmacao(Acao.DESATIVACAO, idConta, autenticacao, model);
		}
		redirecionamento.addFlashAttribute("contaDesativada", contas.conta(idConta).getNome());
		return PARA_A_LISTA;
	}

	@GetMapping(LISTA + "/{idConta}/reativacao")
	public String confirmarReativacao(@PathVariable long idConta, Authentication autenticacao, Model model) {
		return confirmacao(Acao.REATIVACAO, idConta, autenticacao, model);
	}

	@PostMapping(LISTA + "/{idConta}/reativacao")
	public String reativar(@PathVariable long idConta, RedirectAttributes redirecionamento) {
		return contas.reativar(idConta)
				.map(senha -> {
					redirecionamento.addFlashAttribute(SENHA_GERADA, senha);
					return PARA_A_SENHA;
				})
				// Já estava ativa: nada a fazer
				.orElse(PARA_A_LISTA);
	}

	// --- Apoio ---

	private String confirmacao(Acao acao, long idConta, Authentication autenticacao, Model model) {
		LinhaDaConta conta = linha(contas.conta(idConta), autenticacao);
		model.addAttribute(CONTA, conta);
		model.addAttribute("acao", acao);
		model.addAttribute("titulo", acao.titulo(conta.nome()));
		model.addAttribute("explicacao", acao.explicacao(conta.nome()));
		model.addAttribute("erro", erroDaAcao(acao, conta));
		return TELA_CONFIRMAR;
	}

	/** A ação não cabe nesta conta? A página mostra o motivo e não mostra o botão. Textos [PROP]. */
	private static String erroDaAcao(Acao acao, LinhaDaConta conta) {
		if (acao == Acao.DESATIVACAO && conta.propria()) {
			return PROPRIA_CONTA;
		}
		if (acao == Acao.REATIVACAO) {
			return conta.ativa() ? "Esta conta já está ativa." : null;
		}
		if (conta.ativa()) {
			return null;
		}
		return acao == Acao.DESATIVACAO ? "Esta conta já está inativa."
				: "Esta conta está inativa. Para ela voltar a entrar, use “Reativar”, que já gera a nova senha.";
	}

	private LinhaDaConta linha(Usuario conta, Authentication autenticacao) {
		return new LinhaDaConta(conta.getId(), conta.getNome(), conta.getLogin(), conta.isAtivo(),
				DATA.format(conta.getCriadoEm().atZone(fuso)), ContasDaEquipe.propria(conta, autenticacao.getName()));
	}

}
