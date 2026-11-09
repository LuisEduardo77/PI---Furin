package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import br.com.furin.access.portaria.LeituraController.PedidoDeLeitura;
import br.com.furin.access.portaria.LeituraController.RespostaDaLeitura;
import br.com.furin.access.portaria.Leituras.Desfecho;
import br.com.furin.access.portaria.Leituras.ResultadoDaLeitura;

/**
 * Deadlock na gravação da liberação (DEC-46): o MySQL desfaz a transação de uma das leituras
 * simultâneas, e o controller a repete numa transação nova, com a mesma chave (RN08). A
 * corrida de verdade fica em {@link LeituraConcorrenteTests}; aqui o deadlock é provocado.
 */
class LeituraNoDeadlockTests {

	private static final String CODIGO = "c0b2c3d4e5f60718293a4b5c6d7e8f00";
	private static final Authentication FUNCIONARIO = new TestingAuthenticationToken("deadlock.teste", null);

	private final Leituras leituras = mock(Leituras.class);
	private final LeituraController controller = new LeituraController(leituras);

	@Test
	void leituraEscolhidaComoVitimaDoDeadlockEhRepetidaComAMesmaChave() {
		UUID chave = UUID.randomUUID();
		when(leituras.ler(CODIGO, chave, "deadlock.teste"))
				.thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
				.thenReturn(new ResultadoDaLeitura(liberacao(), Desfecho.NOVA));

		ResponseEntity<RespostaDaLeitura> resposta = controller.ler(new PedidoDeLeitura(CODIGO, chave), FUNCIONARIO);

		assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(resposta.getBody().idLiberacao()).isEqualTo(7L);
		verify(leituras, times(2)).ler(CODIGO, chave, "deadlock.teste");
	}

	@Test
	void recusaDaNovaTentativaChegaAoFuncionarioComoARecusaNormal() {
		UUID chave = UUID.randomUUID();
		when(leituras.ler(CODIGO, chave, "deadlock.teste"))
				.thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"))
				.thenThrow(new LeituraRecusada(MotivoDaRecusa.OUTRA_LIBERACAO_PENDENTE, null));
		PedidoDeLeitura pedido = new PedidoDeLeitura(CODIGO, chave);

		assertThatThrownBy(() -> controller.ler(pedido, FUNCIONARIO)).isInstanceOf(LeituraRecusada.class);
	}

	@Test
	void deadlockQueSeRepeteDesisteDepoisDoLimite() {
		when(leituras.ler(anyString(), any(UUID.class), eq("deadlock.teste")))
				.thenThrow(new CannotAcquireLockException("Deadlock found when trying to get lock"));
		PedidoDeLeitura pedido = new PedidoDeLeitura(CODIGO, UUID.randomUUID());

		assertThatThrownBy(() -> controller.ler(pedido, FUNCIONARIO)).isInstanceOf(CannotAcquireLockException.class);
		verify(leituras, times(LeituraController.TENTATIVAS_NO_DEADLOCK)).ler(anyString(), any(UUID.class),
				eq("deadlock.teste"));
	}

	private static Liberacao liberacao() {
		return new Liberacao(7L, TipoMovimentacao.ENTRADA, "AGUARDANDO_GIRO", "Ana Teste", "Ana", "6A — Manhã",
				"foto.png", Instant.parse("2026-10-20T10:00:00Z"), null, null);
	}

}
