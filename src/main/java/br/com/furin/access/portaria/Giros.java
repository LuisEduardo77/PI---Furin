package br.com.furin.access.portaria;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Giro da catraca (T-28; RF09, RF14, passo 7; RN02, passo 3): numa transação, com o
 * aluno bloqueado (RN08), a liberação ainda {@code AGUARDANDO_GIRO} passa a
 * {@code CONFIRMADA}, com {@code ocorrido_em} = instante do giro, dado pelo servidor.
 * Um reenvio com a mesma {@code chaveRequisicao} devolve o resultado original (RN08;
 * DEC-41). Um giro novo, com outra chave, numa liberação já confirmada ou cancelada é
 * recusado e não muda nada (CA-RF09-05).
 * <p>
 * No protótipo, o giro é simulado (DEC-18): só o {@link GiroSimuladoController} chama
 * este serviço. Ficam para depois: o prazo de 15 s (T-29) e a notificação (RF21, Fase 6).
 */
@Service
public class Giros {

	/** Por que um giro foi recusado. Os textos para a tela vêm com a T-30/T-31. */
	public enum MotivoDoGiroRecusado {
		/** Não existe liberação com esse ID. */
		LIBERACAO_NAO_ENCONTRADA,
		/** A liberação já foi confirmada ou cancelada (CA-RF09-05). */
		LIBERACAO_ENCERRADA
	}

	/** Giro recusado; desfaz a transação. */
	public static class GiroRecusado extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final MotivoDoGiroRecusado motivo;

		GiroRecusado(MotivoDoGiroRecusado motivo) {
			super(motivo.name(), null, false, false);
			this.motivo = motivo;
		}

		public MotivoDoGiroRecusado getMotivo() {
			return motivo;
		}
	}

	private final RegistrosDaPortaria registros;

	private final Clock relogio;

	public Giros(RegistrosDaPortaria registros, Clock relogio) {
		this.registros = registros;
		this.relogio = relogio;
	}

	/**
	 * @param idLiberacao     a liberação devolvida pela leitura
	 * @param chaveRequisicao UUID gerado pelo navegador para este giro (RN08; DEC-41)
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Liberacao confirmar(long idLiberacao, UUID chaveRequisicao) {
		long idAluno = registros.alunoDaLiberacao(idLiberacao)
				.orElseThrow(() -> new GiroRecusado(MotivoDoGiroRecusado.LIBERACAO_NAO_ENCONTRADA));
		registros.bloquearAluno(idAluno);

		// RN08: conferida depois do bloqueio, para ver o que um envio simultâneo gravou
		Optional<Long> jaConfirmada = registros.registroPorChaveDoGiro(chaveRequisicao);
		if (jaConfirmada.isPresent()) {
			return registros.liberacao(jaConfirmada.get()).orElseThrow();
		}

		// O instante do giro é sempre do servidor (DATETIME(6): microssegundos)
		Instant giro = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		if (!registros.confirmar(idLiberacao, giro, chaveRequisicao)) {
			throw new GiroRecusado(MotivoDoGiroRecusado.LIBERACAO_ENCERRADA);
		}
		return registros.liberacao(idLiberacao).orElseThrow();
	}

}
