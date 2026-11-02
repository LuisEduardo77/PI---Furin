package br.com.furin.access.portaria;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.furin.access.config.FurinProperties;

/**
 * Tela da portaria (T-30; RF09, RF10, RF14, RF22; DEC-06, DEC-25): campo de leitura, painel
 * do funcionário, monitor do aluno e, só no perfil {@code prototipo}, o painel "Simular
 * leitura (protótipo)" e o botão "Simular passagem (giro)". A leitura e o giro correm em
 * static/js/portaria.js, que chama {@code POST /portaria/leituras} e
 * {@code POST /portaria/giros}.
 * <p>
 * Os tempos do monitor (4 s) e da limpeza do painel (60 s) vêm de
 * {@code furin.portaria.*}. A foto ainda não é entregue (a área privada é da T-18): o
 * painel mostra "Foto indisponível", sem bloquear a liberação (DEC-28, A9).
 */
@Controller
public class PortariaController {

	private final ObjectProvider<CarteirinhasFicticias> simulacao;

	private final FurinProperties.Portaria tempos;

	public PortariaController(ObjectProvider<CarteirinhasFicticias> simulacao, FurinProperties propriedades) {
		this.simulacao = simulacao;
		this.tempos = propriedades.portaria();
	}

	@GetMapping("/portaria")
	public String exibir(Model model) {
		// Sem o bean (fora do perfil prototipo), não há simulação nenhuma na tela
		CarteirinhasFicticias ficticias = simulacao.getIfAvailable();
		model.addAttribute("simulacao", ficticias != null);
		model.addAttribute("carteirinhasFicticias", ficticias == null ? List.of() : ficticias.ativas());
		model.addAttribute("monitorExibicaoMs", tempos.monitorExibicao().toMillis());
		model.addAttribute("painelLimpezaMs", tempos.painelLimpeza().toMillis());
		return "portaria/inicio";
	}

}
