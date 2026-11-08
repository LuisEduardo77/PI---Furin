package br.com.furin.access.portaria;

/**
 * A situação de uma liberação, para a tela da portaria (T-29; RF09, fluxos A1 e A2): se
 * ela continua pendente, foi confirmada ou foi cancelada, e por quê. Só o primeiro nome,
 * para "Liberação expirada — Ana não passou" (RN17).
 *
 * @param motivoCancelamento {@code SEM_GIRO} ou {@code FUNCIONARIO}; {@code null} se não
 *                           foi cancelada
 */
public record SituacaoDaLiberacao(long idLiberacao, String situacao, String motivoCancelamento,
		String primeiroNome) {
}
