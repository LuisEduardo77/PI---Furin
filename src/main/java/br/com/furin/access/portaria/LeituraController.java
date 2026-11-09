package br.com.furin.access.portaria;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.dao.PessimisticLockingFailureException;
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
 * A resposta tem só o que o CA-RNF05-03 lista (DEC-43). O status diz o que aconteceu
 * (T-27; RF15): {@code 201}, liberação nova; {@code 200}, já estava pendente, "Aguardando
 * passagem" (RN04); {@code 202}, leitura ignorada na janela de 2 s, "Leitura já recebida"
 * (RN03), com a liberação pendente no corpo ou sem corpo se ela já não está pendente. Um
 * reenvio com a mesma chave repete o {@code 201} da primeira resposta (RN08). A recusa da
 * RN05 é um {@code 422} com o motivo {@code OUTRA_LIBERACAO_PENDENTE}.
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

	/**
	 * Quantas vezes a leitura é tentada quando o MySQL a escolhe como vítima de um deadlock.
	 * É um limite técnico, e não uma regra de negócio: na prática a segunda tentativa já
	 * resolve.
	 */
	static final int TENTATIVAS_NO_DEADLOCK = 3;

	private final Leituras leituras;

	public LeituraController(Leituras leituras) {
		this.leituras = leituras;
	}

	@PostMapping("/portaria/leituras")
	public ResponseEntity<RespostaDaLeitura> ler(@Valid @RequestBody PedidoDeLeitura pedido,
			Authentication funcionario) {
		ResultadoDaLeitura resultado = lerComNovaTentativa(pedido, funcionario.getName());
		HttpStatus situacao = switch (resultado.desfecho()) {
			case NOVA -> HttpStatus.CREATED;
			case AGUARDANDO_PASSAGEM -> HttpStatus.OK;
			case JA_RECEBIDA -> HttpStatus.ACCEPTED;
		};
		if (resultado.liberacao() == null) {
			return ResponseEntity.status(situacao).build();
		}
		return ResponseEntity.status(situacao).body(RespostaDaLeitura.de(resultado.liberacao()));
	}

	/**
	 * DEC-46: duas leituras de alunos diferentes ao mesmo tempo disputam a
	 * {@code uk_registro_pendente_ponto}. Em geral a segunda recebe a chave duplicada e vira a
	 * recusa {@code OUTRA_LIBERACAO_PENDENTE}, mas o InnoDB às vezes resolve a disputa como
	 * deadlock e desfaz a transação inteira de uma delas, inclusive o bloqueio do aluno (RN08).
	 * Por isso a leitura é repetida aqui, fora da transação, numa transação nova e com a mesma
	 * {@code chaveRequisicao}: a nova tentativa vê a liberação da outra e recusa normalmente,
	 * ou devolve a original se a primeira tiver gravado (RN08).
	 */
	private ResultadoDaLeitura lerComNovaTentativa(PedidoDeLeitura pedido, String login) {
		for (int tentativa = 1;; tentativa++) {
			try {
				return leituras.ler(pedido.codigo(), pedido.chaveRequisicao(), login);
			} catch (PessimisticLockingFailureException deadlock) {
				if (tentativa >= TENTATIVAS_NO_DEADLOCK) {
					throw deadlock;
				}
			}
		}
	}

	@ExceptionHandler(LeituraRecusada.class)
	ResponseEntity<Recusa> recusada(LeituraRecusada recusa) {
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
				.body(new Recusa(recusa.getMotivo(), recusa.getSaidaPendenteDe()));
	}

}
