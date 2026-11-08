package br.com.furin.access.portaria;

import java.time.Instant;

/**
 * A liberação, como está no banco. É a resposta do giro, com {@code ocorridoEm} e o
 * horário pronto para a tela ("07:02"), no fuso da escola (RN10; T-30). A leitura responde
 * só com {@link LeituraController.RespostaDaLeitura} (CA-RNF05-03).
 */
public record Liberacao(
		long idLiberacao,
		TipoMovimentacao tipo,
		String situacao,
		String nome,
		String primeiroNome,
		String turma,
		String fotoChave,
		Instant lancadoEm,
		Instant ocorridoEm,
		String horarioNaEscola) {

	/** RF10: "6A — Manhã". O turno vem de {@code turma.turno}. */
	public static String turmaComTurno(String turma, String turno) {
		return turma + " — " + nomeDoTurno(turno);
	}

	/** "Manhã" para {@code MANHA}, como na tela (RF10, RF16). */
	public static String nomeDoTurno(String turno) {
		return switch (turno) {
			case "MANHA" -> "Manhã";
			case "TARDE" -> "Tarde";
			case "INTEGRAL" -> "Integral";
			case "NOITE" -> "Noite";
			default -> throw new IllegalArgumentException("Turno desconhecido: " + turno);
		};
	}

	static String primeiroNomeDe(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
