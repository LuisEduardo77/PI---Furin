package br.com.furin.access.aluno;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

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

	/** Um consentimento e o nome do aluno, para a tela da revogação. */
	public record Consentimento(long id, long idAluno, String nomeDoAluno, String versao, LocalDate assinadoEm,
			Instant conferidoEm, Instant revogadoEm) {
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

	/**
	 * RF06, matrícula, passo 2: procura entre <b>todos</b> os alunos, ativos e inativos, na
	 * collation da tabela (os zeros à esquerda contam). Na edição (T-23), o próprio aluno
	 * fica de fora: {@code idIgnorado} é ele; no cadastro, é nulo.
	 *
	 * @return o ID do aluno que já usa a matrícula, para o link "Abrir cadastro existente" (T-23)
	 */
	Optional<Long> alunoComMatricula(String matricula, Long idIgnorado) {
		return jdbc.queryForList("SELECT id_aluno FROM aluno WHERE matricula = ? AND (? IS NULL OR id_aluno <> ?)",
				Long.class, matricula, idIgnorado, idIgnorado).stream().findFirst();
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

	long idDoUsuario(String login) {
		return jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Long.class, login);
	}

	/** RF05, passo 3: conferente e instante vêm do servidor (CA-RF05-04). */
	long inserirConsentimento(long idContato, String versao, LocalDate assinadoEm, String referencia,
			long idConferente, Instant agora) {
		return inserir("INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, "
				+ "id_usuario_conferente, conferido_em, revogado_em) VALUES (?, ?, ?, ?, ?, ?, NULL)",
				idContato, versao, assinadoEm, referencia, idConferente, utc(agora));
	}

	// --- Revogação (RF05, fluxo A2; DEC-34) ---

	/** O consentimento, só se for de um contato deste aluno. */
	Optional<Consentimento> consentimentoDoAluno(long idAluno, long idConsentimento) {
		return jdbc.query("SELECT c.id_consentimento, a.id_aluno, a.nome, c.versao_termo, c.assinado_em, "
				+ "c.conferido_em, c.revogado_em FROM consentimento c "
				+ "JOIN contato_responsavel r ON r.id_contato = c.id_contato "
				+ "JOIN aluno a ON a.id_aluno = r.id_aluno WHERE a.id_aluno = ? AND c.id_consentimento = ?",
				(rs, linha) -> new Consentimento(rs.getLong("id_consentimento"), rs.getLong("id_aluno"),
						rs.getString("nome"), rs.getString("versao_termo"),
						rs.getObject("assinado_em", LocalDate.class), instante(rs, "conferido_em"),
						instante(rs, "revogado_em")),
				idAluno, idConsentimento).stream().findFirst();
	}

	/** RN08: a revogação começa bloqueando o aluno, como toda operação sobre ele. */
	void bloquearAluno(long idAluno) {
		jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE id_aluno = ? FOR UPDATE", Long.class, idAluno);
	}

	/** RF05, A2, passo 4: o aluno está na escola (última confirmada é ENTRADA) ou tem liberação pendente. */
	boolean comSaidaPendente(long idAluno) {
		List<String> ultima = jdbc.queryForList("SELECT r.tipo FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ? AND r.situacao = 'CONFIRMADA' "
				+ "ORDER BY r.ocorrido_em DESC, r.id_registro DESC LIMIT 1", String.class, idAluno);
		Integer pendentes = jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "WHERE v.id_aluno = ? AND r.situacao = 'AGUARDANDO_GIRO'", Integer.class, idAluno);
		return ultima.contains("ENTRADA") || (pendentes != null && pendentes > 0);
	}

	boolean revogarConsentimento(long idConsentimento, Instant revogadoEm) {
		return jdbc.update("UPDATE consentimento SET revogado_em = ? WHERE id_consentimento = ? AND revogado_em IS NULL",
				utc(revogadoEm), idConsentimento) == 1;
	}

	void inativarAluno(long idAluno) {
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", idAluno);
	}

	void revogarCarteirinhas(long idAluno, Instant agora) {
		jdbc.update("UPDATE carteirinha SET revogada_em = ? WHERE id_aluno = ? AND revogada_em IS NULL", utc(agora),
				idAluno);
	}

	/** DEC-34, DEC-37: contato inativo não recebe aviso por e-mail. */
	void inativarContatos(long idAluno) {
		jdbc.update("UPDATE contato_responsavel SET ativo = 0 WHERE id_aluno = ?", idAluno);
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

	private static Instant instante(ResultSet rs, String coluna) throws SQLException {
		LocalDateTime utc = rs.getObject(coluna, LocalDateTime.class);
		return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
	}

}
