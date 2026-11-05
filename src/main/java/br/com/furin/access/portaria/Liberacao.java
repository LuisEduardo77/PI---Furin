package br.com.furin.access.portaria;

import java.time.Instant;

/**
 * A liberação devolvida à tela depois da leitura (RF09, passo 4): tipo, nome, primeiro
 * nome (para o monitor do aluno, RF22) e turma. Também serve para a resposta do giro, já
 * com {@code ocorridoEm} e o horário pronto para a tela ("07:02"), no fuso da escola
 * (RN10; T-30). {@code repetida} marca a leitura de um aluno que já tinha esta liberação
 * pendente (RN04): nada novo foi gravado. A referência da foto entra com a área privada
 * (T-18) [PROP].
 */
public record Liberacao(
		long idLiberacao,
		TipoMovimentacao tipo,
		String situacao,
		String nome,
		String primeiroNome,
		String turma,
		Instant lancadoEm,
		Instant ocorridoEm,
		String horarioNaEscola,
		boolean repetida) {

	/** A mesma liberação, como resposta a uma leitura repetida (RN04). */
	Liberacao comoLeituraRepetida() {
		return new Liberacao(idLiberacao, tipo, situacao, nome, primeiroNome, turma, lancadoEm, ocorridoEm,
				horarioNaEscola, true);
	}

	/** RF10: "6A — Manhã". O turno vem de {@code turma.turno}. */
	static String turmaComTurno(String turma, String turno) {
		String nomeDoTurno = switch (turno) {
			case "MANHA" -> "Manhã";
			case "TARDE" -> "Tarde";
			case "INTEGRAL" -> "Integral";
			case "NOITE" -> "Noite";
			default -> throw new IllegalArgumentException("Turno desconhecido: " + turno);
		};
		return turma + " — " + nomeDoTurno;
	}

	static String primeiroNomeDe(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
