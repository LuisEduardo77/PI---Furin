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

import br.com.furin.access.notificacao.AvisoDaSimulacao;
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
 * própria resposta do giro, sem recarregar. Vem também o aviso de simulação do e-mail ao
 * responsável (T-39; DEC-28, F3), que a tela mostra fora das regiões vivas.
 */
@RestController
@Profile("prototipo")
public class GiroSimuladoController {

	/** O ID da liberação devolvido pela leitura e a chave deste giro (RN08; DEC-41). */
	public record PedidoDeGiro(@NotNull @Positive Long idLiberacao, @NotNull UUID chaveRequisicao) {
	}

	/**
	 * A liberação, com os campos no primeiro nível como antes da T-34, os presentes e o aviso
	 * de simulação do e-mail ao responsável (T-39), nulo quando não há notificação.
	 */
	public record RespostaDoGiro(@JsonUnwrapped Liberacao liberacao, PainelDePresentes.Resumo presentes,
			AvisoDaSimulacao.Aviso avisoAoResponsavel) {
	}

	/** A recusa do giro, com o motivo técnico. */
	public record Recusa(MotivoDoGiroRecusado motivo) {
	}

	private final Giros giros;

	private final PainelDePresentes painel;

	private final AvisoDaSimulacao avisoDaSimulacao;

	public GiroSimuladoController(Giros giros, PainelDePresentes painel, AvisoDaSimulacao avisoDaSimulacao) {
		this.giros = giros;
		this.painel = painel;
		this.avisoDaSimulacao = avisoDaSimulacao;
	}

	@PostMapping("/portaria/giros")
	public RespostaDoGiro girar(@Valid @RequestBody PedidoDeGiro pedido) {
		Liberacao liberacao = giros.confirmar(pedido.idLiberacao(), pedido.chaveRequisicao());
		// Depois do commit do giro: o contador já vê a movimentação confirmada. O aviso é montado
		// do registro e não espera o envio, que corre em outra thread (CA-RF21-07)
		return new RespostaDoGiro(liberacao, painel.resumoDepoisDe(liberacao.idLiberacao()),
				avisoDaSimulacao.doRegistro(liberacao.idLiberacao()).orElse(null));
	}

	@ExceptionHandler(GiroRecusado.class)
	ResponseEntity<Recusa> recusado(GiroRecusado recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new Recusa(recusa.getMotivo()));
	}

}
