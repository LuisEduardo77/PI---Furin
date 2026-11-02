package br.com.furin.access.autenticacao;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Leva os tempos da sessão a todas as telas, para o aviso de expiração do layout-base
 * (T-16; templates/layout/base.html e static/js/sessao.js). Os números não ficam no
 * JavaScript: vêm de {@code furin.sessao.*}.
 */
@ControllerAdvice
public class TemposDaSessaoNaTela {

	private final TemposDaSessao tempos;

	public TemposDaSessaoNaTela(TemposDaSessao tempos) {
		this.tempos = tempos;
	}

	@ModelAttribute("sessaoInatividadeSegundos")
	long inatividade() {
		return tempos.inatividadeEmSegundos();
	}

	@ModelAttribute("sessaoAvisoSegundos")
	long avisoAntes() {
		return tempos.avisoAntesEmSegundos();
	}

}
