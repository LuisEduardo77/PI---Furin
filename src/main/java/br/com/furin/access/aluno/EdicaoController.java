package br.com.furin.access.aluno;

import static br.com.furin.access.aluno.ConferenciasDoAluno.CAMPO_MATRICULA;
import static br.com.furin.access.aluno.ConferenciasDoAluno.MATRICULA_REPETIDA;
import static br.com.furin.access.aluno.ConferenciasDoAluno.enviado;

import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.EdicaoDoAluno.Alteracao;
import br.com.furin.access.aluno.EdicaoDoAluno.Diferenca;
import br.com.furin.access.aluno.EdicaoDoAluno.SaidaPendente;
import br.com.furin.access.aluno.RegistrosDeAlunos.CadastroAtual;
import br.com.furin.access.aluno.RegistrosDeAlunos.TurmaAtiva;
import br.com.furin.access.carteirinha.Carteirinhas;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Edição do aluno (T-23; RF08):
 * <ul>
 * <li>{@code GET /alunos/{id}/editar}: o formulário com os dados atuais e as outras ações do
 * aluno (segunda via, revogação do termo, inativação);</li>
 * <li>{@code POST /alunos/{id}/editar/revisar}: confere tudo e mostra o resumo "antes → depois"
 * com Corrigir e Confirmar (A11 §5.9.12);</li>
 * <li>{@code POST /alunos/{id}/editar}: grava o que foi revisado;</li>
 * <li>{@code GET/POST /alunos/{id}/inativacao}: inativar, com confirmação (fluxo A2).</li>
 * </ul>
 * O que foi revisado fica na sessão até a confirmação, com a foto nova, porque o navegador não
 * reenvia o arquivo e porque assim o que se confirma é exatamente o que se viu [PROP].
 */
@Controller
public class EdicaoController {

	private static final Logger LOG = LoggerFactory.getLogger(EdicaoController.class);

	private static final String TELA_EDITAR = "alunos/editar";
	private static final String TELA_INATIVACAO = "alunos/inativacao";
	private static final String PENDENTE = "edicaoDoAluno";
	private static final String PARA_A_EDICAO = "redirect:/alunos/%d/editar";

	/** O nome do formulário no modelo, no binder e nos erros. */
	private static final String FORMULARIO = "edicao";
	private static final String ALUNO = "aluno";

	/**
	 * O que foi revisado e espera a confirmação, guardado na sessão (por isso serializável).
	 * A foto nova vai como bytes, copiados na entrada e na saída.
	 */
	static final class EdicaoPendente implements Serializable {

		private static final long serialVersionUID = 1L;

		private final long idAluno;
		private final EdicaoForm valores;
		private final Alteracao alteracao;
		private final byte[] foto;

		EdicaoPendente(long idAluno, EdicaoForm valores, Alteracao alteracao, byte[] foto) {
			this.idAluno = idAluno;
			this.valores = valores;
			this.alteracao = alteracao;
			this.foto = foto == null ? null : foto.clone();
		}

		long idAluno() {
			return idAluno;
		}

		EdicaoForm valores() {
			return valores;
		}

		Alteracao alteracao() {
			return alteracao;
		}

		/** Nula quando a foto atual continua. */
		ByteArrayResource foto() {
			return foto == null ? null : new ByteArrayResource(foto.clone());
		}
	}

	private final RegistrosDeAlunos registros;

	private final ConferenciasDoAluno conferencias;

	private final EdicaoDoAluno edicao;

	private final Carteirinhas carteirinhas;

	private final Clock relogio;

	public EdicaoController(RegistrosDeAlunos registros, ConferenciasDoAluno conferencias, EdicaoDoAluno edicao,
			Carteirinhas carteirinhas, Clock relogio) {
		this.registros = registros;
		this.conferencias = conferencias;
		this.edicao = edicao;
		this.carteirinhas = carteirinhas;
		this.relogio = relogio;
	}

	/** Espaços nas pontas não contam, como no cadastro. */
	@InitBinder(FORMULARIO)
	void limparEspacos(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
	}

	@GetMapping("/alunos/{idAluno}/editar")
	public String editar(@PathVariable long idAluno, @RequestParam(required = false) String corrigir,
			HttpSession sessao, Model model) {
		CadastroAtual atual = atual(idAluno);
		EdicaoPendente pendente = pendente(sessao, idAluno);
		// CA-RF08-01: "Corrigir" volta ao formulário com os valores digitados
		EdicaoForm form = corrigir != null && pendente != null ? pendente.valores() : comOsDadosAtuais(atual);
		sessao.removeAttribute(PENDENTE);
		model.addAttribute(FORMULARIO, form);
		return telaDeEdicao(atual, model);
	}

	@PostMapping("/alunos/{idAluno}/editar/revisar")
	public String revisar(@PathVariable long idAluno, @Valid @ModelAttribute(FORMULARIO) EdicaoForm form,
			BindingResult erros, HttpSession sessao, Model model) throws IOException {
		CadastroAtual atual = atual(idAluno);
		List<TurmaAtiva> turmas = registros.turmasAtivas();
		Optional<TurmaAtiva> turma = conferencias.turma(form.getIdTurma(), turmas, erros);
		Optional<String> telefone = conferencias.telefone(form.getContatoTelefone(), erros);
		conferencias.dominioDoEmail(form.getContatoEmail(), erros);
		conferencias.matricula(form.getMatricula(), idAluno, erros)
				.ifPresent(existente -> model.addAttribute("matriculaEmUso", existente));
		conferencias.foto(form.getFoto(), false, erros);
		conferirPessoaDoContato(atual, form, erros);
		if (erros.hasErrors()) {
			return telaDeEdicao(atual, model);
		}
		Alteracao alteracao = new Alteracao(form.getNome(), form.getMatricula(), turma.orElseThrow().id(),
				turma.orElseThrow().nome(), form.isSubstituirContato(), form.getContatoNome(), telefone.orElseThrow(),
				form.getContatoEmail(), form.isRecebeAviso());
		boolean fotoNova = enviado(form.getFoto());
		List<Diferenca> diferencas = edicao.diferencas(atual, alteracao, fotoNova);
		if (diferencas.isEmpty()) {
			model.addAttribute("semAlteracoes", true);
			return telaDeEdicao(atual, model);
		}
		byte[] foto = fotoNova ? form.getFoto().getBytes() : null;
		sessao.setAttribute(PENDENTE, new EdicaoPendente(idAluno, form.semFoto(), alteracao, foto));
		model.addAttribute(ALUNO, atual);
		model.addAttribute("diferencas", diferencas);
		model.addAttribute("avisoMuda", atual.recebeAviso() != alteracao.recebeAviso());
		model.addAttribute("alteracao", alteracao);
		return "alunos/revisar";
	}

	@PostMapping("/alunos/{idAluno}/editar")
	public String confirmar(@PathVariable long idAluno, HttpSession sessao, Model model,
			RedirectAttributes redirecionamento) {
		CadastroAtual atual = atual(idAluno);
		EdicaoPendente pendente = pendente(sessao, idAluno);
		if (pendente == null) {
			redirecionamento.addFlashAttribute("revisaoPerdida", true);
			return PARA_A_EDICAO.formatted(idAluno);
		}
		model.addAttribute(FORMULARIO, pendente.valores());
		BindingResult erros = new BeanPropertyBindingResult(pendente.valores(), FORMULARIO);
		model.addAttribute(BindingResult.MODEL_KEY_PREFIX + FORMULARIO, erros);
		try {
			edicao.gravar(idAluno, pendente.alteracao(), pendente.foto());
			sessao.removeAttribute(PENDENTE);
			// RF08, passo 6
			redirecionamento.addFlashAttribute("cadastroAtualizado", true);
			return PARA_A_EDICAO.formatted(idAluno);
		} catch (MatriculaJaCadastrada repetida) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
			registros.alunoComMatricula(pendente.alteracao().matricula(), idAluno)
					.ifPresent(existente -> model.addAttribute("matriculaEmUso", existente));
		} catch (IOException | DataAccessException falha) {
			LOG.error("Falha ao gravar a edição de um aluno", falha);
			erros.reject("falha", "Não foi possível salvar as alterações. Tente novamente.");
		}
		sessao.removeAttribute(PENDENTE);
		return telaDeEdicao(atual, model);
	}

	@GetMapping("/alunos/{idAluno}/inativacao")
	public String confirmarInativacao(@PathVariable long idAluno, Model model) {
		model.addAttribute(ALUNO, atual(idAluno));
		return TELA_INATIVACAO;
	}

	@PostMapping("/alunos/{idAluno}/inativacao")
	public String inativar(@PathVariable long idAluno, Model model, RedirectAttributes redirecionamento) {
		CadastroAtual atual = atual(idAluno);
		try {
			edicao.inativar(idAluno);
			redirecionamento.addFlashAttribute("alunoInativado", true);
			return PARA_A_EDICAO.formatted(idAluno);
		} catch (SaidaPendente pendente) {
			// RF08, exceções [PROP: texto]
			model.addAttribute("erro", "Registre a saída de " + primeiroNome(atual.nome())
					+ " antes de inativar o cadastro.");
		}
		model.addAttribute(ALUNO, atual);
		return TELA_INATIVACAO;
	}

	/** RF08, fluxo A1: sem a substituição, o nome do contato não muda [PROP: texto]. */
	private static void conferirPessoaDoContato(CadastroAtual atual, EdicaoForm form, BindingResult erros) {
		if (!form.isSubstituirContato() && atual.idContato() != null && !erros.hasFieldErrors("contatoNome")
				&& !Objects.equals(atual.contatoNome(), form.getContatoNome())) {
			erros.rejectValue("contatoNome", "pessoa", "O nome do contato só muda com a substituição do contato "
					+ "principal. Marque “Substituir o contato principal por outra pessoa”.");
		}
	}

	private String telaDeEdicao(CadastroAtual atual, Model model) {
		model.addAttribute(ALUNO, atual);
		model.addAttribute("turmas", registros.turmasAtivas());
		model.addAttribute("regraDaFoto", conferencias.regraDaFoto());
		model.addAttribute("carteirinhaValida", carteirinhas.valida(atual.idAluno()).orElse(null));
		model.addAttribute("consentimentoValido", registros.consentimentoValido(atual.idAluno()).orElse(null));
		return TELA_EDITAR;
	}

	private static EdicaoForm comOsDadosAtuais(CadastroAtual atual) {
		EdicaoForm form = new EdicaoForm();
		form.setNome(atual.nome());
		form.setMatricula(atual.matricula());
		form.setIdTurma(atual.idTurma() == null ? null : String.valueOf(atual.idTurma()));
		form.setContatoNome(atual.contatoNome());
		form.setContatoTelefone(atual.contatoTelefone());
		form.setContatoEmail(atual.contatoEmail());
		form.setRecebeAviso(atual.idContato() == null || atual.recebeAviso());
		return form;
	}

	private CadastroAtual atual(long idAluno) {
		return registros.cadastroAtual(idAluno, relogio.instant())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static EdicaoPendente pendente(HttpSession sessao, long idAluno) {
		return sessao.getAttribute(PENDENTE) instanceof EdicaoPendente pendente && pendente.idAluno() == idAluno
				? pendente
				: null;
	}

	static String primeiroNome(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
