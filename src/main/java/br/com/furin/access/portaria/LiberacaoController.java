package br.com.furin.access.portaria;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.furin.access.portaria.Liberacoes.CancelamentoRecusado;
import br.com.furin.access.portaria.Liberacoes.MotivoDoCancelamentoRecusado;

/**
 * Rotas da liberação em curso (T-29; RF09, fluxos A1 e A2; arquitetura §4), para qualquer
 * funcionário (DEC-37), com o token CSRF no POST:
 * <ul>
 * <li>{@code POST /portaria/liberacoes/{id}/cancelar}: o botão "Cancelar liberação de
 * Ana Teste" (RN02, item 5). Uma recusa responde 422 com o motivo;</li>
 * <li>{@code GET /portaria/liberacoes/{id}} [PROP]: a situação, que a tela consulta quando
 * passa o prazo do giro para mostrar "Liberação expirada — Ana não passou".</li>
 * </ul>
 */
@RestController
public class LiberacaoController {

	/** A recusa do cancelamento, com o motivo técnico. */
	public record Recusa(MotivoDoCancelamentoRecusado motivo) {
	}

	private final Liberacoes liberacoes;

	public LiberacaoController(Liberacoes liberacoes) {
		this.liberacoes = liberacoes;
	}

	@PostMapping("/portaria/liberacoes/{id}/cancelar")
	public SituacaoDaLiberacao cancelar(@PathVariable long id) {
		return liberacoes.cancelar(id);
	}

	@GetMapping("/portaria/liberacoes/{id}")
	public ResponseEntity<SituacaoDaLiberacao> situacao(@PathVariable long id) {
		return ResponseEntity.of(liberacoes.situacao(id));
	}

	@ExceptionHandler(CancelamentoRecusado.class)
	ResponseEntity<Recusa> recusado(CancelamentoRecusado recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Recusa(recusa.getMotivo()));
	}

}
