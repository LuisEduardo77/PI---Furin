package br.com.furin.access.presenca;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.furin.access.config.FurinProperties;

/**
 * {@code GET /presentes} (T-34; RF16; arquitetura §4): "Presentes agora", "Por turma" e
 * "Saídas pendentes", com "Registrar saída de Ana Teste" levando ao registro manual da T-32
 * ({@code /portaria/manual/{idAluno}}).
 * <p>
 * static/js/presentes.js pede esta mesma página de novo a cada
 * {@code furin.presentes.intervalo-atualizacao} (DEC-28, C2) e quando o funcionário aciona
 * "Atualizar", e troca só os números. A atualização automática vai com o cabeçalho
 * {@link br.com.furin.access.autenticacao.AtualizacaoAutomatica#CABECALHO}: não conta como
 * atividade, e a sessão expira por inatividade do mesmo jeito (T-16).
 */
@Controller
public class PresentesController {

	private final PainelDePresentes painel;

	private final FurinProperties.Presentes tempos;

	public PresentesController(PainelDePresentes painel, FurinProperties propriedades) {
		this.painel = painel;
		this.tempos = propriedades.presentes();
	}

	@GetMapping("/presentes")
	public String exibir(Model model) {
		model.addAttribute("painel", painel.calcular());
		model.addAttribute("intervaloAtualizacaoMs", tempos.intervaloAtualizacao().toMillis());
		return "presenca/presentes";
	}

}
