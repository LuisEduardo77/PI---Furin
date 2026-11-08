package br.com.furin.access.notificacao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * As consultas do envio das notificações (T-39), em SQL explícito como as da portaria. Só
 * a tabela {@code notificacao} é alterada (CA-RF21-06). Horários em UTC (RN10).
 */
@Repository
class RegistrosDeNotificacoes {

	private final JdbcTemplate jdbc;

	RegistrosDeNotificacoes(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<Long> pendentesDoRegistro(long idRegistro) {
		return jdbc.queryForList("SELECT id_notificacao FROM notificacao WHERE id_registro = ? AND status = 'PENDENTE' "
				+ "ORDER BY id_notificacao", Long.class, idRegistro);
	}

	/**
	 * Reserva uma tentativa: soma 1 em {@code tentativas} se a notificação ainda espera envio
	 * ({@code PENDENTE} ou {@code FALHA}) e não chegou ao limite. A condição no próprio
	 * {@code UPDATE} impede duas threads de entregarem a mesma notificação.
	 *
	 * @return o número desta tentativa, ou 0 se não há o que tentar
	 */
	int reservarTentativa(long idNotificacao, Instant agora, int maxTentativas) {
		int alteradas = jdbc.update("UPDATE notificacao SET tentativas = tentativas + 1, ultima_tentativa_em = ? "
				+ "WHERE id_notificacao = ? AND status IN ('PENDENTE', 'FALHA') AND tentativas < ?",
				LocalDateTime.ofInstant(agora, ZoneOffset.UTC), idNotificacao, maxTentativas);
		if (alteradas == 0) {
			return 0;
		}
		return jdbc.queryForObject("SELECT tentativas FROM notificacao WHERE id_notificacao = ?", Integer.class,
				idNotificacao);
	}

	void marcar(long idNotificacao, String status) {
		jdbc.update("UPDATE notificacao SET status = ? WHERE id_notificacao = ?", status, idNotificacao);
	}

}
