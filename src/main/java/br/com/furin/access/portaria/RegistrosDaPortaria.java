package br.com.furin.access.portaria;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.furin.access.portaria.TipoAutomatico.UltimaMovimentacao;

/**
 * Consultas da portaria em SQL explícito (T-26; RN08; arquitetura §5.5), com
 * {@code JdbcTemplate}: o bloqueio do aluno ({@code SELECT … FOR UPDATE}) fica visível, e
 * os horários vão e voltam em UTC como {@code LocalDateTime} sem conversão de fuso (RN10).
 * Todas as consultas usam parâmetros, nunca texto concatenado.
 */
@Repository
public class RegistrosDaPortaria {

	static final String AGUARDANDO_GIRO = "AGUARDANDO_GIRO";
	static final String CONFIRMADA = "CONFIRMADA";

	/** A carteirinha lida. */
	public record Carteirinha(long id, long idAluno, Instant expiraEm, Instant revogadaEm) {
	}

	/** O aluno, já bloqueado na transação. */
	public record Aluno(long id, String nome, boolean ativo) {
	}

	/** O vínculo de turma vigente, gravado em {@code registro_acesso.id_vinculo}. */
	public record Vinculo(long id, String turma) {
	}

	private static final String LIBERACAO = "SELECT r.id_registro, r.tipo, r.situacao, r.lancado_em, r.ocorrido_em, "
			+ "a.nome, t.nome AS turma FROM registro_acesso r "
			+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
			+ "JOIN aluno a ON a.id_aluno = v.id_aluno "
			+ "JOIN turma t ON t.id_turma = v.id_turma ";

	private final JdbcTemplate jdbc;

	public RegistrosDaPortaria(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	Optional<Carteirinha> carteirinhaPorCodigo(String codigo) {
		return jdbc.query("SELECT id_carteirinha, id_aluno, expira_em, revogada_em FROM carteirinha WHERE codigo = ?",
				(rs, linha) -> new Carteirinha(rs.getLong("id_carteirinha"), rs.getLong("id_aluno"),
						instante(rs, "expira_em"), instante(rs, "revogada_em")),
				codigo).stream().findFirst();
	}

	/** RN08: toda operação sobre o aluno começa por aqui, e a transação segura o bloqueio. */
	Aluno bloquearAluno(long idAluno) {
		return jdbc.queryForObject("SELECT id_aluno, nome, ativo FROM aluno WHERE id_aluno = ? FOR UPDATE",
				(rs, linha) -> new Aluno(rs.getLong("id_aluno"), rs.getString("nome"), rs.getBoolean("ativo")),
				idAluno);
	}

	Optional<Long> registroPorChave(UUID chaveRequisicao) {
		return jdbc.queryForList("SELECT id_registro FROM registro_acesso WHERE chave_requisicao = ?", Long.class,
				chaveRequisicao.toString()).stream().findFirst();
	}

	/** Vigente agora: começou e não terminou. Se houver mais de um, vale o mais recente. */
	Optional<Vinculo> vinculoVigente(long idAluno, Instant agora) {
		LocalDateTime utc = utc(agora);
		return jdbc.query("SELECT v.id_vinculo, t.nome FROM vinculo_turma v JOIN turma t ON t.id_turma = v.id_turma "
				+ "WHERE v.id_aluno = ? AND v.inicio_em <= ? AND (v.fim_em IS NULL OR v.fim_em > ?) "
				+ "ORDER BY v.inicio_em DESC LIMIT 1",
				(rs, linha) -> new Vinculo(rs.getLong("id_vinculo"), rs.getString("nome")),
				idAluno, utc, utc).stream().findFirst();
	}

	boolean temLiberacaoPendente(long idAluno) {
		Integer pendentes = jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ? AND r.situacao = ?",
				Integer.class, idAluno, AGUARDANDO_GIRO);
		return pendentes != null && pendentes > 0;
	}

	/** RN01; RF13, passo 1: a última CONFIRMADA, por {@code ocorrido_em} e, no empate, pelo ID. */
	Optional<UltimaMovimentacao> ultimaConfirmada(long idAluno) {
		return jdbc.query("SELECT r.tipo, r.ocorrido_em FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ? AND r.situacao = ? "
				+ "ORDER BY r.ocorrido_em DESC, r.id_registro DESC LIMIT 1",
				(rs, linha) -> new UltimaMovimentacao(TipoMovimentacao.valueOf(rs.getString("tipo")),
						instante(rs, "ocorrido_em")),
				idAluno, CONFIRMADA).stream().findFirst();
	}

	long idDoUsuario(String login) {
		return jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Long.class, login);
	}

	/** RN02, passo 1: liberação {@code AGUARDANDO_GIRO}, {@code forma = LEITURA}. */
	long gravarLiberacao(Vinculo vinculo, long idUsuario, long idCarteirinha, TipoMovimentacao tipo, Instant agora,
			UUID chaveRequisicao) {
		KeyHolder chave = new GeneratedKeyHolder();
		jdbc.update(conexao -> {
			PreparedStatement sql = conexao.prepareStatement("INSERT INTO registro_acesso (id_vinculo, id_usuario, "
					+ "id_carteirinha, tipo, forma, situacao, lancado_em, chave_requisicao) "
					+ "VALUES (?, ?, ?, ?, 'LEITURA', ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			sql.setLong(1, vinculo.id());
			sql.setLong(2, idUsuario);
			sql.setLong(3, idCarteirinha);
			sql.setString(4, tipo.name());
			sql.setString(5, AGUARDANDO_GIRO);
			sql.setObject(6, utc(agora));
			sql.setString(7, chaveRequisicao.toString());
			return sql;
		}, chave);
		return chave.getKey().longValue();
	}

	Optional<Liberacao> liberacao(long idRegistro) {
		return jdbc.query(LIBERACAO + "WHERE r.id_registro = ?", RegistrosDaPortaria::liberacao, idRegistro)
				.stream().findFirst();
	}

	/** O aluno de uma liberação, para bloqueá-lo antes do giro (RN08). */
	Optional<Long> alunoDaLiberacao(long idRegistro) {
		return jdbc.queryForList("SELECT v.id_aluno FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE r.id_registro = ?", Long.class, idRegistro)
				.stream().findFirst();
	}

	/** RN02, passo 3: só uma liberação ainda pendente vira CONFIRMADA. */
	boolean confirmar(long idRegistro, Instant giro) {
		return jdbc.update("UPDATE registro_acesso SET situacao = ?, ocorrido_em = ? WHERE id_registro = ? AND situacao = ?",
				CONFIRMADA, utc(giro), idRegistro, AGUARDANDO_GIRO) == 1;
	}

	private static Liberacao liberacao(ResultSet rs, int linha) throws SQLException {
		String nome = rs.getString("nome");
		Instant ocorridoEm = instante(rs, "ocorrido_em");
		return new Liberacao(rs.getLong("id_registro"), TipoMovimentacao.valueOf(rs.getString("tipo")),
				rs.getString("situacao"), nome, Liberacao.primeiroNomeDe(nome), rs.getString("turma"),
				instante(rs, "lancado_em"), ocorridoEm, Liberacao.horarioNaEscola(ocorridoEm));
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}

	private static Instant instante(ResultSet rs, String coluna) throws SQLException {
		LocalDateTime utc = rs.getObject(coluna, LocalDateTime.class);
		return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
	}

}
