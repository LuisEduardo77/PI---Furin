package br.com.furin.access.portaria;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela inicial dos funcionários (RF03; DEC-37). Por enquanto, só a página com o menu
 * (T-12); a leitura da carteirinha e os painéis vêm a partir da T-25.
 */
@Controller
public class PortariaController {

	@GetMapping("/portaria")
	public String exibir() {
		return "portaria/inicio";
	}

}
