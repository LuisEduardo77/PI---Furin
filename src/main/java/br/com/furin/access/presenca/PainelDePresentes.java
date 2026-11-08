package br.com.furin.access.presenca;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.Liberacao;

/**
 * Painel de presentes (T-34; RF16; RN12 com o refinamento do DEC-28, C1 e C3). Tudo é
 * calculado na hora, a partir das movimentações {@code CONFIRMADA}; nada é gravado (A14
 * p.4). Liberações {@code AGUARDANDO_GIRO} e {@code CANCELADA} não contam (CA-RF16-02).
 * <ul>
 * <li><b>Presentes agora:</b> alunos ativos cuja última movimentação confirmada é uma
 * {@code ENTRADA} de hoje, no fuso da escola (RN10);</li>
 * <li><b>Por turma:</b> os presentes pela turma do vínculo vigente agora (CA-RF16-05), com
 * todas as turmas ativas, inclusive as que têm zero;</li>
 * <li><b>Saídas pendentes:</b> alunos ativos cuja última movimentação confirmada é uma
 * {@code ENTRADA} de dia anterior (RN06). Ficam fora do total.</li>
 * </ul>
 * O aluno inativo fica de fora das duas listas: o registro manual (RF12) não aceita aluno
 * inativo. Consultas em SQL explícito, com parâmetros; os horários vão em UTC (RN10).
 */
@Service
public class PainelDePresentes {

	/** O painel calculado. {@code atualizadoAs} é a hora do cálculo na escola ("09:41"). */
	public record Painel(int total, List<Turma> turmas, List<Pendente> pendentes, String atualizadoAs) {
	}

	/** Uma linha de "Por turma". Sem turma vigente, {@code idTurma}, {@code nome} e {@code turno} são nulos. */
	public record Turma(Long idTurma, String nome, String turno, int presentes) {
	}

	/** Uma saída pendente, com a entrada já no fuso da escola ("16/10 07:02"). */
	public record Pendente(long idAluno, String nome, String turma, String entradaEm) {
	}

	/** O contador resumido da portaria (RF16, passo 4): o total e o da turma do registro. */
	public record Resumo(int total, String turma, Integer presentesNaTurma) {
	}

	/** Um aluno cuja última movimentação confirmada é uma ENTRADA. */
	private record ComEntrada(long idAluno, String nome, Instant entradaEm, Long idTurma, String turma, String turno) {
	}

	/**
	 * A última CONFIRMADA de cada aluno (mesma ordem da RN01: {@code ocorrido_em} e, no empate,
	 * o ID) e o vínculo vigente agora, o mais recente se houver mais de um.
	 */
	private static final String COM_ENTRADA = "WITH ultimas AS ("
			+ "SELECT v.id_aluno, r.tipo, r.ocorrido_em, ROW_NUMBER() OVER ("
			+ "PARTITION BY v.id_aluno ORDER BY r.ocorrido_em DESC, r.id_registro DESC) AS ordem "
			+ "FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo WHERE r.situacao = 'CONFIRMADA'"
			+ "), vigentes AS ("
			+ "SELECT v.id_aluno, v.id_turma, ROW_NUMBER() OVER ("
			+ "PARTITION BY v.id_aluno ORDER BY v.inicio_em DESC, v.id_vinculo DESC) AS ordem "
			+ "FROM vinculo_turma v WHERE v.inicio_em <= ? AND (v.fim_em IS NULL OR v.fim_em > ?)"
			+ ") "
			+ "SELECT a.id_aluno, a.nome, u.ocorrido_em, t.id_turma, t.nome AS turma, t.turno "
			+ "FROM ultimas u JOIN aluno a ON a.id_aluno = u.id_aluno "
			+ "LEFT JOIN vigentes g ON g.id_aluno = u.id_aluno AND g.ordem = 1 "
			+ "LEFT JOIN turma t ON t.id_turma = g.id_turma "
			+ "WHERE u.ordem = 1 AND u.tipo = 'ENTRADA' AND a.ativo = 1 "
			+ "ORDER BY u.ocorrido_em, a.nome, a.id_aluno";

	private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DIA_E_HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

	private final JdbcTemplate jdbc;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	public PainelDePresentes(JdbcTemplate jdbc, Clock relogio, FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
	}

	@Transactional(readOnly = true)
	public Painel calcular() {
		Instant agora = relogio.instant();
		// RN10: "hoje" começa à meia-noite no fuso da escola
		Instant inicioDeHoje = LocalDate.ofInstant(agora, fusoDaEscola).atStartOfDay(fusoDaEscola).toInstant();

		// DEC-28, C3: todas as turmas ativas, inclusive as que ficam com zero
		Map<Long, Turma> turmas = new LinkedHashMap<>();
		Map<Long, Integer> porTurma = new LinkedHashMap<>();
		jdbc.query("SELECT id_turma, nome, turno FROM turma WHERE ativa = 1 ORDER BY nome, turno, id_turma", rs -> {
			long id = rs.getLong("id_turma");
			turmas.put(id, new Turma(id, rs.getString("nome"), Liberacao.nomeDoTurno(rs.getString("turno")), 0));
		});

		int total = 0;
		int semTurma = 0;
		List<Pendente> pendentes = new ArrayList<>();
		for (ComEntrada aluno : comEntrada(agora)) {
			if (aluno.entradaEm().isBefore(inicioDeHoje)) {
				pendentes.add(new Pendente(aluno.idAluno(), aluno.nome(),
						aluno.turma() == null ? null : Liberacao.turmaComTurno(aluno.turma(), aluno.turno()),
						DIA_E_HORA.format(aluno.entradaEm().atZone(fusoDaEscola))));
				continue;
			}
			total++;
			if (aluno.idTurma() == null) {
				semTurma++;
			} else {
				// Uma turma já desativada com aluno presente também aparece
				turmas.computeIfAbsent(aluno.idTurma(),
						id -> new Turma(id, aluno.turma(), Liberacao.nomeDoTurno(aluno.turno()), 0));
				porTurma.merge(aluno.idTurma(), 1, Integer::sum);
			}
		}

		List<Turma> linhas = new ArrayList<>();
		turmas.forEach((id, turma) -> linhas.add(new Turma(id, turma.nome(), turma.turno(), porTurma.getOrDefault(id, 0))));
		if (semTurma > 0) {
			linhas.add(new Turma(null, null, null, semTurma));
		}
		return new Painel(total, List.copyOf(linhas), List.copyOf(pendentes), HORA.format(agora.atZone(fusoDaEscola)));
	}

	/**
	 * RF16, passo 4: o contador da portaria depois de um giro confirmado, com o total e a
	 * turma do vínculo vigente do aluno do registro.
	 */
	@Transactional(readOnly = true)
	public Resumo resumoDepoisDe(long idRegistro) {
		Painel painel = calcular();
		Optional<Long> turmaDoAluno = turmaVigenteDoAlunoDo(idRegistro);
		return turmaDoAluno.flatMap(id -> painel.turmas().stream().filter(turma -> id.equals(turma.idTurma())).findFirst())
				.map(turma -> new Resumo(painel.total(), turma.nome() + " — " + turma.turno(), turma.presentes()))
				.orElseGet(() -> new Resumo(painel.total(), null, null));
	}

	private Optional<Long> turmaVigenteDoAlunoDo(long idRegistro) {
		LocalDateTime agora = utc(relogio.instant());
		return jdbc.queryForList("SELECT g.id_turma FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "JOIN vinculo_turma g ON g.id_aluno = v.id_aluno AND g.inicio_em <= ? AND (g.fim_em IS NULL OR g.fim_em > ?) "
				+ "WHERE r.id_registro = ? ORDER BY g.inicio_em DESC, g.id_vinculo DESC LIMIT 1",
				Long.class, agora, agora, idRegistro).stream().findFirst();
	}

	private List<ComEntrada> comEntrada(Instant agora) {
		LocalDateTime utc = utc(agora);
		return jdbc.query(COM_ENTRADA, (rs, linha) -> {
			long idTurma = rs.getLong("id_turma");
			boolean semTurma = rs.wasNull();
			return new ComEntrada(rs.getLong("id_aluno"), rs.getString("nome"),
					rs.getObject("ocorrido_em", LocalDateTime.class).toInstant(ZoneOffset.UTC),
					semTurma ? null : idTurma, rs.getString("turma"), rs.getString("turno"));
		}, utc, utc);
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}

}
