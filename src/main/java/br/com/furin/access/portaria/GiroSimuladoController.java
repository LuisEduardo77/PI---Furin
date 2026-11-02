package br.com.furin.access.portaria;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.furin.access.portaria.Giros.GiroRecusado;
import br.com.furin.access.portaria.Giros.MotivoDoGiroRecusado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /portaria/giros}: o giro <b>simulado</b> do botão "Simular passagem (giro)"
 * (T-28; RF09, passo 6; DEC-18, DEC-25). Só existe no perfil {@code prototipo}: fora
 * dele, a rota responde 404 (CA-RF09-08). Na integração física (fora do MVP), o giro
 * viria do sensor da catraca.
 */
@RestController
@Profile("prototipo")
public class GiroSimuladoController {

	/** O ID da liberação devolvido pela leitura. */
	public record PedidoDeGiro(@NotNull @Positive Long idLiberacao) {
	}

	/** A recusa do giro, com o motivo técnico. */
	public record Recusa(MotivoDoGiroRecusado motivo) {
	}

	private final Giros giros;

	public GiroSimuladoController(Giros giros) {
		this.giros = giros;
	}

	@PostMapping("/portaria/giros")
	public Liberacao girar(@Valid @RequestBody PedidoDeGiro pedido) {
		return giros.confirmar(pedido.idLiberacao());
	}

	@ExceptionHandler(GiroRecusado.class)
	ResponseEntity<Recusa> recusado(GiroRecusado recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Recusa(recusa.getMotivo()));
	}

}
