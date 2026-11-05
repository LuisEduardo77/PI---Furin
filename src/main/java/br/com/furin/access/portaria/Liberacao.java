package br.com.furin.access.portaria;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import br.com.furin.access.config.RelogioConfig;

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

	private static final DateTimeFormatter HORA_E_MINUTO = DateTimeFormatter.ofPattern("HH:mm")
			.withZone(RelogioConfig.FUSO_DA_ESCOLA);

	/** A mesma liberação, como resposta a uma leitura repetida (RN04). */
	Liberacao comoLeituraRepetida() {
		return new Liberacao(idLiberacao, tipo, situacao, nome, primeiroNome, turma, lancadoEm, ocorridoEm,
				horarioNaEscola, true);
	}

	/** "07:02" no fuso da escola, ou {@code null} enquanto não houve giro. */
	static String horarioNaEscola(Instant ocorridoEm) {
		return ocorridoEm == null ? null : HORA_E_MINUTO.format(ocorridoEm);
	}

	static String primeiroNomeDe(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
