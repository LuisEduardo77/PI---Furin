package br.com.furin.access.aluno;

import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.furin.access.aluno.BuscaDeAlunos.Escopo;
import br.com.furin.access.aluno.BuscaDeAlunos.Resultado;

/**
 * {@code GET /alunos}: a tela Alunos, com o link "Novo aluno" e a busca por nome ou matrícula
 * (T-24; RF11). A busca é um GET: não muda nada e pode ser repetida pelo endereço. Os
 * inativos só aparecem com o filtro "Incluir inativos" (DEC-28, C4).
 */
@Controller
public class BuscaController {

	private final BuscaDeAlunos busca;

	public BuscaController(BuscaDeAlunos busca) {
		this.busca = busca;
	}

	@GetMapping("/alunos")
	public String alunos(@RequestParam(required = false) String termo,
			@RequestParam(defaultValue = "false") boolean inativos, Model model) {
		model.addAttribute("termo", termo == null ? "" : termo.strip());
		model.addAttribute("inativos", inativos);
		// Sem termo no endereço, a tela abre sem busca e sem mensagem
		if (termo != null) {
			Optional<String> problema = busca.problemaNoTermo(termo);
			if (problema.isPresent()) {
				model.addAttribute("erroDoTermo", problema.get());
			} else {
				Resultado resultado = busca.buscar(termo, inativos ? Escopo.COM_INATIVOS : Escopo.SO_ATIVOS);
				model.addAttribute("resultado", resultado);
				model.addAttribute("maxResultados", busca.maxResultados());
			}
		}
		return "alunos/inicio";
	}

}
