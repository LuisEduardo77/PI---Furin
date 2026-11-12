package br.com.furin.access.portaria;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import br.com.furin.access.config.FurinProperties;
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
	static final String CANCELADA = "CANCELADA";
	static final String SEM_GIRO = "SEM_GIRO";
	static final String FUNCIONARIO = "FUNCIONARIO";

	/** Uma liberação que passou do prazo do giro, com o aluno para bloquear (RN08). */
	public record Vencida(long idLiberacao, long idAluno) {
	}

	/**
	 * O aluno para o registro manual (T-32; RF12, passo 4): {@code idVinculo} e {@code turma}
	 * são nulos sem vínculo vigente.
	 */
	public record AlunoDoManual(long id, String nome, String matricula, boolean ativo, Long idVinculo, String turma) {
	}

	/** Um registro manual já gravado, para responder ao reenvio da mesma chave (RN08). */
	public record Manual(long idRegistro, long idAluno, TipoMovimentacao tipo, Instant ocorridoEm, String nome) {
	}

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
			+ "a.nome, a.foto_chave, t.nome AS turma, t.turno FROM registro_acesso r "
			+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
			+ "JOIN aluno a ON a.id_aluno = v.id_aluno "
			+ "JOIN turma t ON t.id_turma = v.id_turma ";

	/** Colunas lidas em várias consultas. */
	private static final String ID_ALUNO = "id_aluno";

	private static final String ID_REGISTRO = "id_registro";

	private static final String OCORRIDO_EM = "ocorrido_em";

	private final JdbcTemplate jdbc;

	/** "07:02" no fuso da escola (RN10). */
	private final DateTimeFormatter horaNaEscola;

	public RegistrosDaPortaria(JdbcTemplate jdbc, FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.horaNaEscola = DateTimeFormatter.ofPattern("HH:mm").withZone(propriedades.escola().fuso());
	}

	Optional<Carteirinha> carteirinhaPorCodigo(String codigo) {
		return jdbc.query("SELECT id_carteirinha, id_aluno, expira_em, revogada_em FROM carteirinha WHERE codigo = ?",
				(rs, linha) -> new Carteirinha(rs.getLong("id_carteirinha"), rs.getLong(ID_ALUNO),
						instante(rs, "expira_em"), instante(rs, "revogada_em")),
				codigo).stream().findFirst();
	}

	/** RN08: toda operação sobre o aluno começa por aqui, e a transação segura o bloqueio. */
	Aluno bloquearAluno(long idAluno) {
		return jdbc.queryForObject("SELECT id_aluno, nome, ativo FROM aluno WHERE id_aluno = ? FOR UPDATE",
				(rs, linha) -> new Aluno(rs.getLong(ID_ALUNO), rs.getString("nome"), rs.getBoolean("ativo")),
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

	/** RN04: a liberação do aluno que ainda espera o giro, se houver. O banco garante no máximo uma. */
	Optional<Liberacao> liberacaoPendente(long idAluno) {
		return jdbc.query(LIBERACAO + "WHERE v.id_aluno = ? AND r.situacao = ?", this::liberacaoDaLinha,
				idAluno, AGUARDANDO_GIRO).stream().findFirst();
	}

	/**
	 * RN03 (DEC-02; arquitetura §3.1): a leitura mais recente desta carteirinha com
	 * {@code lancado_em} a partir de {@code desde} e ainda não confirmada. Uma liberação
	 * {@code CONFIRMADA} quer dizer que houve giro, e o giro encerra a janela.
	 */
	Optional<Liberacao> leituraNaJanela(long idCarteirinha, Instant desde) {
		return jdbc.query(LIBERACAO + "WHERE r.id_carteirinha = ? AND r.lancado_em >= ? AND r.situacao <> ? "
				+ "ORDER BY r.lancado_em DESC, r.id_registro DESC LIMIT 1", this::liberacaoDaLinha,
				idCarteirinha, utc(desde), CONFIRMADA).stream().findFirst();
	}

	/** RN05 (DEC-46): há alguma liberação pendente no ponto de leitura? */
	boolean existeLiberacaoPendente() {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM registro_acesso WHERE pendente_ponto = 1)", Boolean.class));
	}

	/** RN01; RF13, passo 1: a última CONFIRMADA, por {@code ocorrido_em} e, no empate, pelo ID. */
	Optional<UltimaMovimentacao> ultimaConfirmada(long idAluno) {
		return jdbc.query("SELECT r.tipo, r.ocorrido_em FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ? AND r.situacao = ? "
				+ "ORDER BY r.ocorrido_em DESC, r.id_registro DESC LIMIT 1",
				(rs, linha) -> new UltimaMovimentacao(TipoMovimentacao.valueOf(rs.getString("tipo")),
						instante(rs, OCORRIDO_EM)),
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
		return jdbc.query(LIBERACAO + "WHERE r.id_registro = ?", this::liberacaoDaLinha, idRegistro)
				.stream().findFirst();
	}

	/** O aluno de uma liberação, para bloqueá-lo antes do giro (RN08). */
	Optional<Long> alunoDaLiberacao(long idRegistro) {
		return jdbc.queryForList("SELECT v.id_aluno FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE r.id_registro = ?", Long.class, idRegistro)
				.stream().findFirst();
	}

	/** DEC-41: o registro já confirmado por um giro com esta chave, se houver. */
	Optional<Long> registroPorChaveDoGiro(UUID chaveDoGiro) {
		return jdbc.queryForList("SELECT id_registro FROM registro_acesso WHERE chave_giro = ?", Long.class,
				chaveDoGiro.toString()).stream().findFirst();
	}

	/**
	 * RN02, passo 3: só uma liberação ainda pendente e lançada depois de {@code limite}
	 * (dentro do prazo do giro; RF09, passo 7.2) vira CONFIRMADA. A chave fica gravada (DEC-41).
	 */
	boolean confirmar(long idRegistro, Instant giro, UUID chaveDoGiro, Instant limite) {
		return jdbc.update("UPDATE registro_acesso SET situacao = ?, ocorrido_em = ?, chave_giro = ? "
				+ "WHERE id_registro = ? AND situacao = ? AND lancado_em > ?",
				CONFIRMADA, utc(giro), chaveDoGiro.toString(), idRegistro, AGUARDANDO_GIRO, utc(limite)) == 1;
	}

	/** RN02, item 4: as liberações ainda pendentes lançadas até {@code limite}. */
	List<Vencida> vencidas(Instant limite) {
		return jdbc.query("SELECT r.id_registro, v.id_aluno FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE r.situacao = ? AND r.lancado_em <= ? "
				+ "ORDER BY r.id_registro",
				(rs, linha) -> new Vencida(rs.getLong(ID_REGISTRO), rs.getLong(ID_ALUNO)),
				AGUARDANDO_GIRO, utc(limite));
	}

	/** RN02, item 4: cancela com SEM_GIRO, se ela ainda estiver pendente e vencida. */
	boolean cancelarVencida(long idRegistro, Instant agora, Instant limite) {
		return jdbc.update("UPDATE registro_acesso SET situacao = ?, cancelado_em = ?, motivo_cancelamento = ? "
				+ "WHERE id_registro = ? AND situacao = ? AND lancado_em <= ?",
				CANCELADA, utc(agora), SEM_GIRO, idRegistro, AGUARDANDO_GIRO, utc(limite)) == 1;
	}

	/** RN02, item 5 (DEC-28, A2): só uma liberação pendente é cancelada; a confirmada nunca muda. */
	boolean cancelarPeloFuncionario(long idRegistro, Instant agora) {
		return jdbc.update("UPDATE registro_acesso SET situacao = ?, cancelado_em = ?, motivo_cancelamento = ? "
				+ "WHERE id_registro = ? AND situacao = ?",
				CANCELADA, utc(agora), FUNCIONARIO, idRegistro, AGUARDANDO_GIRO) == 1;
	}

	/** RF12, passo 4: nome, matrícula, turma vigente e se o aluno está ativo, sem bloquear. */
	Optional<AlunoDoManual> alunoDoManual(long idAluno, Instant agora) {
		LocalDateTime utc = utc(agora);
		return jdbc.query("SELECT a.id_aluno, a.nome, a.matricula, a.ativo, v.id_vinculo, t.nome AS turma, t.turno "
				+ "FROM aluno a LEFT JOIN vinculo_turma v ON v.id_aluno = a.id_aluno AND v.inicio_em <= ? "
				+ "AND (v.fim_em IS NULL OR v.fim_em > ?) LEFT JOIN turma t ON t.id_turma = v.id_turma "
				+ "WHERE a.id_aluno = ? ORDER BY v.inicio_em DESC LIMIT 1",
				(rs, linha) -> {
					long idVinculo = rs.getLong("id_vinculo");
					boolean semVinculo = rs.wasNull();
					return new AlunoDoManual(rs.getLong(ID_ALUNO), rs.getString("nome"), rs.getString("matricula"),
							rs.getBoolean("ativo"), semVinculo ? null : idVinculo,
							semVinculo ? null : Liberacao.turmaComTurno(rs.getString("turma"), rs.getString("turno")));
				},
				utc, utc, idAluno).stream().findFirst();
	}

	/**
	 * RN07, item 5 (DEC-28, B2; DEC-20): {@code forma = MANUAL}, {@code CONFIRMADA}, sem
	 * carteirinha, com o horário informado em {@code ocorrido_em} e o do servidor em
	 * {@code lancado_em}.
	 */
	long gravarManual(Vinculo vinculo, long idUsuario, TipoMovimentacao tipo, Instant ocorrido, Instant lancado,
			String motivo, UUID chaveRequisicao) {
		KeyHolder chave = new GeneratedKeyHolder();
		jdbc.update(conexao -> {
			PreparedStatement sql = conexao.prepareStatement("INSERT INTO registro_acesso (id_vinculo, id_usuario, "
					+ "id_carteirinha, tipo, forma, situacao, lancado_em, ocorrido_em, chave_requisicao, motivo_manual) "
					+ "VALUES (?, ?, NULL, ?, 'MANUAL', ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			sql.setLong(1, vinculo.id());
			sql.setLong(2, idUsuario);
			sql.setString(3, tipo.name());
			sql.setString(4, CONFIRMADA);
			sql.setObject(5, utc(lancado));
			sql.setObject(6, utc(ocorrido));
			sql.setString(7, chaveRequisicao.toString());
			sql.setString(8, motivo);
			return sql;
		}, chave);
		return chave.getKey().longValue();
	}

	/** O registro manual gravado com esta chave, se houver (RN08). */
	Optional<Manual> manualPorChave(UUID chaveRequisicao) {
		return jdbc.query("SELECT r.id_registro, v.id_aluno, r.tipo, r.ocorrido_em, a.nome FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo JOIN aluno a ON a.id_aluno = v.id_aluno "
				+ "WHERE r.chave_requisicao = ? AND r.forma = 'MANUAL'",
				(rs, linha) -> new Manual(rs.getLong(ID_REGISTRO), rs.getLong(ID_ALUNO),
						TipoMovimentacao.valueOf(rs.getString("tipo")), instante(rs, OCORRIDO_EM), rs.getString("nome")),
				chaveRequisicao.toString()).stream().findFirst();
	}

	/** A situação de uma liberação, para a tela saber se ela expirou ou foi cancelada. */
	Optional<SituacaoDaLiberacao> situacao(long idRegistro) {
		return jdbc.query("SELECT r.id_registro, r.situacao, r.motivo_cancelamento, a.nome FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo JOIN aluno a ON a.id_aluno = v.id_aluno "
				+ "WHERE r.id_registro = ?",
				(rs, linha) -> new SituacaoDaLiberacao(rs.getLong(ID_REGISTRO), rs.getString("situacao"),
						rs.getString("motivo_cancelamento"), Liberacao.primeiroNomeDe(rs.getString("nome"))),
				idRegistro).stream().findFirst();
	}

	private Liberacao liberacaoDaLinha(ResultSet rs, int linha) throws SQLException {
		String nome = rs.getString("nome");
		Instant ocorridoEm = instante(rs, OCORRIDO_EM);
		return new Liberacao(rs.getLong(ID_REGISTRO), TipoMovimentacao.valueOf(rs.getString("tipo")),
				rs.getString("situacao"), nome, Liberacao.primeiroNomeDe(nome),
				Liberacao.turmaComTurno(rs.getString("turma"), rs.getString("turno")), rs.getString("foto_chave"),
				instante(rs, "lancado_em"), ocorridoEm,
				ocorridoEm == null ? null : horaNaEscola.format(ocorridoEm));
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}

	private static Instant instante(ResultSet rs, String coluna) throws SQLException {
		LocalDateTime utc = rs.getObject(coluna, LocalDateTime.class);
		return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
	}

}
