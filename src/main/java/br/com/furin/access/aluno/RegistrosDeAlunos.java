package br.com.furin.access.aluno;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import br.com.furin.access.portaria.Liberacao;

/**
 * Consultas do cadastro de alunos em SQL explícito (T-19), como na portaria: parâmetros
 * sempre, e os horários em UTC como {@code LocalDateTime} (RN10).
 */
@Repository
public class RegistrosDeAlunos {

	/** Uma opção do campo Turma, com o turno ("6A — Manhã", RF10). */
	public record TurmaAtiva(long id, String nome) {
	}

	private final JdbcTemplate jdbc;

	public RegistrosDeAlunos(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	List<TurmaAtiva> turmasAtivas() {
		return jdbc.query("SELECT id_turma, nome, turno FROM turma WHERE ativa = 1 ORDER BY nome",
				(rs, linha) -> new TurmaAtiva(rs.getLong("id_turma"),
						Liberacao.turmaComTurno(rs.getString("nome"), rs.getString("turno"))));
	}

	boolean matriculaCadastrada(String matricula) {
		Integer quantidade = jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = ?", Integer.class,
				matricula);
		return quantidade != null && quantidade > 0;
	}

	long inserirAluno(String matricula, String nome, String fotoChave, Instant agora) {
		return inserir("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, ?, ?, 1, ?)",
				matricula, nome, fotoChave, utc(agora));
	}

	long inserirVinculo(long idAluno, long idTurma, Instant agora) {
		return inserir("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em, fim_em) VALUES (?, ?, ?, NULL)",
				idAluno, idTurma, utc(agora));
	}

	/** RF04, passo 5: principal, com a preferência de aviso em "receber" (DEC-22, DEC-38). */
	long inserirContatoPrincipal(long idAluno, String nome, String telefone, String email) {
		return inserir("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, ?, ?, ?, 1, 1, 1)", idAluno, nome, telefone, email);
	}

	private long inserir(String sql, Object... valores) {
		GeneratedKeyHolder chave = new GeneratedKeyHolder();
		jdbc.update(conexao -> {
			PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < valores.length; i++) {
				comando.setObject(i + 1, valores[i]);
			}
			return comando;
		}, chave);
		return chave.getKey().longValue();
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}

}
