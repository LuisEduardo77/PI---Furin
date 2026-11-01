package br.com.furin.access.exemplo;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Página de exemplo do layout-base (T-08), para conferir a checklist de
 * acessibilidade do plano §3. É temporária: sai quando existir a primeira tela real.
 * Nada é gravado.
 */
@Controller
@RequestMapping("/exemplo/layout")
public class ExemploLayoutController {

	private static final String PAGINA = "exemplo/layout";

	@GetMapping
	public String exibir(Model model) {
		model.addAttribute("exemplo", new ExemploForm());
		return PAGINA;
	}

	@PostMapping
	public String enviar(@Valid @ModelAttribute("exemplo") ExemploForm exemplo, BindingResult erros,
			Model model) {
		if (!erros.hasErrors()) {
			model.addAttribute("enviado", true);
		}
		return PAGINA;
	}

	/** Formulário fictício: mostra rótulo, campo obrigatório, erro e dados preservados. */
	public static class ExemploForm {

		@NotBlank(message = "Informe o nome do aluno.")
		@Size(max = 150, message = "O nome do aluno pode ter até 150 caracteres.")
		private String nome;

		private String turma;

		public String getNome() {
			return nome;
		}

		public void setNome(String nome) {
			this.nome = nome;
		}

		public String getTurma() {
			return turma;
		}

		public void setTurma(String turma) {
			this.turma = turma;
		}

	}

}
