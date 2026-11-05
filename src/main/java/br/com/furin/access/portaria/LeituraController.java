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

import br.com.furin.access.portaria.Leituras.ResultadoDaLeitura;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /portaria/leituras} (T-26; RF09, RF14; arquitetura §3.1 e §4). Recebe JSON
 * com o {@code codigo} e a {@code chaveRequisicao}, com o token CSRF no cabeçalho, e
 * devolve a liberação. Qualquer outro campo, como um {@code tipo}, é ignorado (RN01;
 * CA-RF13-07). Uma recusa responde 422 com o motivo técnico (os textos são da T-31).
 * <p>
 * A resposta tem só o que o CA-RNF05-03 lista (DEC-43). O status diz se a liberação é
 * nova ou se já estava pendente (RN04): {@code 201} ou {@code 200}. Um reenvio com a mesma
 * chave repete o {@code 201} da primeira resposta (RN08).
 */
@RestController
public class LeituraController {

	/** O que chega do navegador. Não existe campo de tipo. */
	public record PedidoDeLeitura(
			@NotBlank @Size(max = 100) String codigo,
			@NotNull UUID chaveRequisicao) {
	}

	/**
	 * CA-RNF05-03: tipo, nomes, turma, referência da foto e ID da liberação, e nada além
	 * disso. A referência é a chave da área privada, que só abre em {@code GET /arquivos/{chave}}.
	 */
	public record RespostaDaLeitura(long idLiberacao, TipoMovimentacao tipo, String nome, String primeiroNome,
			String turma, String fotoChave) {

		static RespostaDaLeitura de(Liberacao liberacao) {
			return new RespostaDaLeitura(liberacao.idLiberacao(), liberacao.tipo(), liberacao.nome(),
					liberacao.primeiroNome(), liberacao.turma(), liberacao.fotoChave());
		}
	}

	/** A recusa: o motivo e, na saída pendente, o dia da entrada (RN06). */
	public record Recusa(MotivoDaRecusa motivo, LocalDate saidaPendenteDe) {
	}

	private final Leituras leituras;

	public LeituraController(Leituras leituras) {
		this.leituras = leituras;
	}

	@PostMapping("/portaria/leituras")
	public ResponseEntity<RespostaDaLeitura> ler(@Valid @RequestBody PedidoDeLeitura pedido,
			Authentication funcionario) {
		ResultadoDaLeitura resultado = leituras.ler(pedido.codigo(), pedido.chaveRequisicao(), funcionario.getName());
		return ResponseEntity.status(resultado.repetida() ? HttpStatus.OK : HttpStatus.CREATED)
				.body(RespostaDaLeitura.de(resultado.liberacao()));
	}

	@ExceptionHandler(LeituraRecusada.class)
	ResponseEntity<Recusa> recusada(LeituraRecusada recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
				.body(new Recusa(recusa.getMotivo(), recusa.getSaidaPendenteDe()));
	}

}
