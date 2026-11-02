package br.com.furin.access.portaria;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /portaria/leituras} (T-26; RF09, RF14; arquitetura §3.1 e §4). Recebe JSON
 * com o {@code codigo} e a {@code chaveRequisicao}, com o token CSRF no cabeçalho, e
 * devolve a liberação. Qualquer outro campo, como um {@code tipo}, é ignorado (RN01;
 * CA-RF13-07). Uma recusa responde 422 com o motivo técnico (os textos são da T-31).
 */
@RestController
public class LeituraController {

	/** O que chega do navegador. Não existe campo de tipo. */
	public record PedidoDeLeitura(
			@NotBlank @Size(max = 100) String codigo,
			@NotNull UUID chaveRequisicao) {
	}

	/** A recusa: o motivo e, na saída pendente, o dia da entrada (RN06). */
	public record Recusa(MotivoDaRecusa motivo, LocalDate saidaPendenteDe) {
	}

	private final Leituras leituras;

	public LeituraController(Leituras leituras) {
		this.leituras = leituras;
	}

	@PostMapping("/portaria/leituras")
	public Liberacao ler(@Valid @RequestBody PedidoDeLeitura pedido, Authentication funcionario) {
		return leituras.ler(pedido.codigo(), pedido.chaveRequisicao(), funcionario.getName());
	}

	@ExceptionHandler(LeituraRecusada.class)
	ResponseEntity<Recusa> recusada(LeituraRecusada recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
				.body(new Recusa(recusa.getMotivo(), recusa.getSaidaPendenteDe()));
	}

}
