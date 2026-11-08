package br.com.furin.access.portaria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.RegistrosDaPortaria.Vencida;

/**
 * Expiração das liberações sem giro (T-29; RF09, fluxo A1; RN02, item 4; DEC-28, A1):
 * uma liberação {@code AGUARDANDO_GIRO} lançada há {@code furin.portaria.prazo-giro} ou
 * mais vira {@code CANCELADA} com {@code SEM_GIRO}. Nada vai para o histórico, para os
 * presentes nem para as notificações, que só olham as {@code CONFIRMADA}; o evento vai
 * para o log.
 * <p>
 * Roda pela {@link ExpiracaoAgendada} e também no começo de cada operação da portaria
 * (leitura, giro, cancelamento e consulta), dentro da transação dela (arquitetura §3.1).
 * Cada liberação é cancelada com o aluno dela bloqueado (RN08). Como só há uma liberação
 * pendente por vez (RN05; DEC-46), a ordem dos bloqueios é sempre a mesma: primeiro o
 * aluno da pendente, depois o da operação.
 */
@Service
public class Expiracoes {

	private final RegistrosDaPortaria registros;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final Duration prazoDoGiro;

	public Expiracoes(RegistrosDaPortaria registros, Auditoria auditoria, Clock relogio,
			FurinProperties propriedades) {
		this.registros = registros;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.prazoDoGiro = propriedades.portaria().prazoGiro();
	}

	/** @return quantas liberações foram canceladas agora */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public int expirarVencidas() {
		Instant agora = agora();
		Instant limite = limite(agora);
		int canceladas = 0;
		for (Vencida vencida : registros.vencidas(limite)) {
			registros.bloquearAluno(vencida.idAluno());
			// Conferida de novo com o aluno bloqueado: um giro pode ter chegado antes
			if (registros.cancelarVencida(vencida.idLiberacao(), agora, limite)) {
				auditoria.registrar(EventoAuditoria.LIBERACAO_EXPIRADA,
						detalhes().id("liberacao", vencida.idLiberacao()).texto("motivo", RegistrosDaPortaria.SEM_GIRO));
				canceladas++;
			}
		}
		return canceladas;
	}

	/** O instante do servidor, em microssegundos como o DATETIME(6). */
	Instant agora() {
		return relogio.instant().truncatedTo(ChronoUnit.MICROS);
	}

	/** Lançada até este instante, a liberação já passou do prazo do giro. */
	Instant limite(Instant agora) {
		return agora.minus(prazoDoGiro);
	}

}
