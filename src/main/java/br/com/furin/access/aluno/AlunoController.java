package br.com.furin.access.aluno;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import br.com.furin.access.aluno.CadastroDeAlunos.CadastroIncompleto;
import br.com.furin.access.aluno.CadastroDeAlunos.MatriculaJaCadastrada;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.aluno.RegistrosDeAlunos.TurmaAtiva;
import br.com.furin.access.arquivo.AreaPrivada;
import br.com.furin.access.arquivo.AreaPrivada.TipoDeDocumento;
import br.com.furin.access.arquivo.ArquivoRecusado;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.FurinProperties.RegraArquivo;
import jakarta.validation.Valid;

/**
 * Telas de alunos (T-19; RF04):
 * <ul>
 * <li>{@code GET /alunos}: a entrada do menu, com o link "Novo aluno". A busca é da T-24;</li>
 * <li>{@code GET /alunos/novo}: o formulário;</li>
 * <li>{@code POST /alunos}: confere tudo e tenta o cadastro completo.</li>
 * </ul>
 * Até a T-22, nada é salvo (ver {@link CadastroDeAlunos}), e a tela diz isso com clareza.
 * Em qualquer erro, os dados válidos voltam preenchidos, menos a foto, que o navegador não
 * permite manter (RF04; A11 §5.9.9).
 */
@Controller
public class AlunoController {

	private static final Logger LOG = LoggerFactory.getLogger(AlunoController.class);

	private static final String TELA_NOVO = "alunos/novo";
	private static final String CAMPO_FOTO = "foto";
	private static final String CAMPO_TURMA = "idTurma";
	private static final String CAMPO_MATRICULA = "matricula";
	private static final String CAMPO_EMAIL = "contatoEmail";
	private static final String MATRICULA_REPETIDA = "Esta matrícula já está cadastrada. Confira a matrícula ou "
			+ "localize o cadastro existente para editá-lo.";

	private final CadastroDeAlunos cadastro;

	private final RegistrosDeAlunos registros;

	private final AreaPrivada areaPrivada;

	private final String ddi;

	private final String regraDaFoto;

	public AlunoController(CadastroDeAlunos cadastro, RegistrosDeAlunos registros, AreaPrivada areaPrivada,
			FurinProperties propriedades) {
		this.cadastro = cadastro;
		this.registros = registros;
		this.areaPrivada = areaPrivada;
		this.ddi = propriedades.contato().ddi();
		this.regraDaFoto = regraEmTexto(propriedades.arquivos().foto());
	}

	/** Espaços nas pontas não contam: um campo só de espaços vale como vazio. */
	@InitBinder("aluno")
	void limparEspacos(WebDataBinder binder) {
		binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
	}

	@ModelAttribute("regraDaFoto")
	String regraDaFoto() {
		return regraDaFoto;
	}

	@GetMapping("/alunos")
	public String inicio() {
		return "alunos/inicio";
	}

	@GetMapping("/alunos/novo")
	public String novo(Model model) {
		model.addAttribute("aluno", new AlunoForm());
		model.addAttribute("turmas", registros.turmasAtivas());
		return TELA_NOVO;
	}

	@PostMapping("/alunos")
	public String cadastrar(@Valid @ModelAttribute("aluno") AlunoForm form, BindingResult erros, Model model) {
		List<TurmaAtiva> turmas = registros.turmasAtivas();
		model.addAttribute("turmas", turmas);
		Optional<TurmaAtiva> turma = conferirTurma(form, turmas, erros);
		Optional<String> telefone = conferirTelefone(form, erros);
		conferirDominioDoEmail(form, erros);
		conferirMatricula(form, erros);
		conferirFoto(form.getFoto(), erros);
		if (erros.hasErrors()) {
			return TELA_NOVO;
		}
		NovoAluno novo = new NovoAluno(form.getNome(), form.getMatricula(), turma.orElseThrow().id(),
				form.getContatoNome(), telefone.orElseThrow(), form.getContatoEmail());
		try (InputStream foto = form.getFoto().getInputStream()) {
			cadastro.cadastrar(novo, foto);
		} catch (CadastroIncompleto faltamBlocos) {
			model.addAttribute("cadastroIncompleto", true);
		} catch (MatriculaJaCadastrada repetida) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
		} catch (IOException | DataAccessException falha) {
			LOG.error("Falha ao gravar o cadastro de um aluno", falha);
			erros.reject("falha", "Não foi possível salvar o cadastro. Tente novamente.");
		}
		return TELA_NOVO;
	}

	private static Optional<TurmaAtiva> conferirTurma(AlunoForm form, List<TurmaAtiva> turmas, BindingResult erros) {
		Optional<TurmaAtiva> turma = turmas.stream()
				.filter(ativa -> String.valueOf(ativa.id()).equals(form.getIdTurma()))
				.findFirst();
		if (turma.isEmpty()) {
			erros.rejectValue(CAMPO_TURMA, "inativa", "Selecione uma turma ativa.");
		}
		return turma;
	}

	private Optional<String> conferirTelefone(AlunoForm form, BindingResult erros) {
		Optional<String> telefone = TelefoneDoContato.normalizar(form.getContatoTelefone(), ddi);
		if (telefone.isEmpty() && !erros.hasFieldErrors("contatoTelefone")) {
			erros.rejectValue("contatoTelefone", "invalido", "Informe o telefone com DDD. Exemplo: (11) 90000-0000.");
		}
		return telefone;
	}

	/** {@code @Email} aceita "carla@escola"; o aviso ao responsável precisa de um domínio completo. */
	private static void conferirDominioDoEmail(AlunoForm form, BindingResult erros) {
		String email = form.getContatoEmail();
		if (!erros.hasFieldErrors(CAMPO_EMAIL) && !email.substring(email.lastIndexOf('@')).contains(".")) {
			erros.rejectValue(CAMPO_EMAIL, "dominio", AlunoForm.EMAIL_INVALIDO);
		}
	}

	private void conferirMatricula(AlunoForm form, BindingResult erros) {
		if (!erros.hasFieldErrors(CAMPO_MATRICULA) && registros.matriculaCadastrada(form.getMatricula())) {
			erros.rejectValue(CAMPO_MATRICULA, "repetida", MATRICULA_REPETIDA);
		}
	}

	private void conferirFoto(MultipartFile foto, BindingResult erros) {
		if (foto == null || foto.isEmpty()) {
			erros.rejectValue(CAMPO_FOTO, "ausente", "Envie a foto do aluno.");
			return;
		}
		try (InputStream conteudo = foto.getInputStream()) {
			areaPrivada.conferir(TipoDeDocumento.FOTO, conteudo);
		} catch (ArquivoRecusado | IOException recusada) {
			erros.rejectValue(CAMPO_FOTO, "regra", "A foto precisa ser " + regraDaFoto + ".");
		}
	}

	/** "JPEG ou PNG, até 2 MB", sempre igual a {@code furin.arquivos.foto.*}. */
	private static String regraEmTexto(RegraArquivo regra) {
		String formatos = regra.tipos().stream()
				.map(tipo -> tipo.substring(tipo.indexOf('/') + 1).toUpperCase(Locale.ROOT))
				.collect(Collectors.joining(" ou "));
		return formatos + ", até " + regra.tamanhoMax().toMegabytes() + " MB";
	}

}
