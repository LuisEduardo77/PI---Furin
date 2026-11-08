package br.com.furin.access.portaria;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import br.com.furin.access.portaria.Giros.GiroRecusado;
import br.com.furin.access.portaria.Giros.MotivoDoGiroRecusado;
import br.com.furin.access.presenca.PainelDePresentes;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * {@code POST /portaria/giros}: o giro <b>simulado</b> do botão "Simular passagem (giro)"
 * (T-28; RF09, passo 6; DEC-18, DEC-25). Só existe no perfil {@code prototipo}: fora
 * dele, a rota responde 404 (CA-RF09-08). Na integração física (fora do MVP), o giro
 * viria do sensor da catraca.
 * <p>
 * A resposta é a {@link Liberacao} e, junto, o contador de presentes calculado depois da
 * confirmação (T-34; RF16, passo 4; CA-RF16-06): a tela da portaria muda o contador com a
 * própria resposta do giro, sem recarregar.
 */
@RestController
@Profile("prototipo")
public class GiroSimuladoController {

	/** O ID da liberação devolvido pela leitura e a chave deste giro (RN08; DEC-41). */
	public record PedidoDeGiro(@NotNull @Positive Long idLiberacao, @NotNull UUID chaveRequisicao) {
	}

	/** A liberação, com os campos no primeiro nível como antes da T-34, e os presentes. */
	public record RespostaDoGiro(@JsonUnwrapped Liberacao liberacao, PainelDePresentes.Resumo presentes) {
	}

	/** A recusa do giro, com o motivo técnico. */
	public record Recusa(MotivoDoGiroRecusado motivo) {
	}

	private final Giros giros;

	private final PainelDePresentes painel;

	public GiroSimuladoController(Giros giros, PainelDePresentes painel) {
		this.giros = giros;
		this.painel = painel;
	}

	@PostMapping("/portaria/giros")
	public RespostaDoGiro girar(@Valid @RequestBody PedidoDeGiro pedido) {
		Liberacao liberacao = giros.confirmar(pedido.idLiberacao(), pedido.chaveRequisicao());
		// Depois do commit do giro: o contador já vê a movimentação confirmada
		return new RespostaDoGiro(liberacao, painel.resumoDepoisDe(liberacao.idLiberacao()));
	}

	@ExceptionHandler(GiroRecusado.class)
	ResponseEntity<Recusa> recusado(GiroRecusado recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Recusa(recusa.getMotivo()));
	}

}
