package br.com.furin.access.portaria;

import java.time.Instant;

/**
 * A liberação devolvida à tela depois da leitura (RF09, passo 4): tipo, nome, primeiro
 * nome (para o monitor do aluno, RF22) e turma. Também serve para a resposta do giro, já
 * com {@code ocorridoEm}. A referência da foto entra com a tela da portaria (T-30) [PROP].
 */
public record Liberacao(
		long idLiberacao,
		TipoMovimentacao tipo,
		String situacao,
		String nome,
		String primeiroNome,
		String turma,
		Instant lancadoEm,
		Instant ocorridoEm) {

	static String primeiroNomeDe(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
