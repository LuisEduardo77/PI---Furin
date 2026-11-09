package br.com.furin.access.aluno;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Apaga, nos testes, alunos gravados de verdade e tudo o que é deles (T-22): desde que o
 * cadastro salva os cinco blocos (RN16), um aluno tem registros em várias tabelas.
 */
public final class LimpezaDeAlunos {

	private LimpezaDeAlunos() {
	}

	/** @param condicao sobre a tabela {@code aluno}, por exemplo {@code "matricula LIKE 'T19-%'"} */
	public static void apagar(JdbcTemplate jdbc, String condicao, Object... valores) {
		List<Long> alunos = jdbc.queryForList("SELECT id_aluno FROM aluno WHERE " + condicao, Long.class, valores);
		for (long aluno : alunos) {
			// T-38: a notificação aponta para o registro e para o contato
			jdbc.update("DELETE n FROM notificacao n JOIN contato_responsavel c ON c.id_contato = n.id_contato "
					+ "WHERE c.id_aluno = ?", aluno);
			jdbc.update("DELETE r FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
					+ "WHERE v.id_aluno = ?", aluno);
			jdbc.update("DELETE FROM carteirinha WHERE id_aluno = ?", aluno);
			jdbc.update("DELETE c FROM consentimento c JOIN contato_responsavel r ON r.id_contato = c.id_contato "
					+ "WHERE r.id_aluno = ?", aluno);
			jdbc.update("DELETE FROM contato_responsavel WHERE id_aluno = ?", aluno);
			jdbc.update("DELETE FROM vinculo_turma WHERE id_aluno = ?", aluno);
			jdbc.update("DELETE FROM aluno WHERE id_aluno = ?", aluno);
		}
	}

}
