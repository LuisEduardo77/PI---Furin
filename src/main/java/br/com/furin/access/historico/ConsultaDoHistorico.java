package br.com.furin.access.historico;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.Liberacao;

/**
 * A consulta do histórico (T-35; RF18, passos 3 e 4; RF19, passo 1; RN10; RN12), em SQL
 * explícito e sempre com parâmetros (A10 §8.28; CA-RF18-06):
 * <ul>
 * <li>só {@code CONFIRMADA} (DEC-19): liberações pendentes e canceladas nunca aparecem;</li>
 * <li>o período vai do começo da data inicial ao fim da data final <b>no fuso da escola</b>,
 * convertido para UTC (CA-RF18-04);</li>
 * <li>a turma é a do vínculo gravado no registro, a do dia do evento (A14 p.6;
 * CA-RF18-02);</li>
 * <li>o aluno é parte do nome ou a matrícula inteira;</li>
 * <li>da mais recente para a mais antiga, {@code furin.historico.tamanho-pagina} por
 * página.</li>
 * </ul>
 * Não lê foto, contato nem código de carteirinha (CA-RF19-08). Nada é alterado.
 */
@Service
public class ConsultaDoHistorico {

	/** Os filtros já conferidos. {@code idTurma} e {@code aluno} são nulos quando não filtram. */
	public record Filtros(LocalDate inicio, LocalDate fim, Long idTurma, String aluno) {
	}

	/**
	 * Uma linha da tabela (RF19, passo 1), já em texto. {@code observacao} é nula nos
	 * registros por leitura (CA-RF19-03).
	 */
	public record Linha(String data, String hora, String aluno, String turma, String tipo, String forma,
			String observacao, String aviso) {
	}

	/** Uma página do resultado. {@code pagina} começa em 1. */
	public record Pagina(List<Linha> linhas, long total, int pagina, int totalDePaginas) {

		public boolean temAnterior() {
			return pagina > 1;
		}

		public boolean temProxima() {
			return pagina < totalDePaginas;
		}
	}

	/** Uma turma para a lista do filtro. */
	public record Turma(long id, String nome) {
	}

	/**
	 * O relatório para impressão (T-36; RF20): todas as linhas do filtro, sem paginação. Acima
	 * de {@code furin.relatorio.max-registros}, {@code excedeu} e nenhuma linha.
	 */
	public record Relatorio(List<Linha> linhas, long total, boolean excedeu) {
	}

	/** O caractere de escape do LIKE; ele mesmo também é escapado. */
	private static final char ESCAPE = '!';

	/**
	 * O SQL é sempre o mesmo texto constante (A10 §8.28): os filtros opcionais são
	 * {@code ? IS NULL OR …}, e um filtro vazio vai como {@code NULL}. Parâmetros, na ordem:
	 * início e fim do período (UTC), a turma duas vezes, o aluno, o padrão do LIKE e o aluno de
	 * novo (parte do nome ou a matrícula inteira, CA-RF18-03).
	 */
	private static final String ORIGEM = " FROM registro_acesso r "
			+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
			+ "JOIN aluno a ON a.id_aluno = v.id_aluno "
			+ "JOIN turma t ON t.id_turma = v.id_turma "
			+ "JOIN usuario u ON u.id_usuario = r.id_usuario "
			+ "WHERE r.situacao = 'CONFIRMADA' AND r.ocorrido_em >= ? AND r.ocorrido_em < ? "
			+ "AND (? IS NULL OR v.id_turma = ?) "
			+ "AND (? IS NULL OR a.nome LIKE ? ESCAPE '!' OR a.matricula = ?)";

	private static final String CONTAGEM = "SELECT COUNT(*)" + ORIGEM;

	/** O aviso da T-38/T-39: há no máximo uma notificação por movimentação (o contato principal). */
	private static final String PAGINA = "SELECT r.id_registro, r.tipo, r.forma, r.ocorrido_em, r.lancado_em, "
			+ "r.motivo_manual, a.nome, a.matricula, t.nome AS turma, t.turno, u.nome AS autor, "
			+ "(SELECT n.status FROM notificacao n WHERE n.id_registro = r.id_registro "
			+ "ORDER BY n.id_notificacao LIMIT 1) AS aviso" + ORIGEM
			+ " ORDER BY r.ocorrido_em DESC, r.id_registro DESC LIMIT ? OFFSET ?";

	private final JdbcTemplate jdbc;

	private final ZoneId fusoDaEscola;

	private final int tamanhoDaPagina;

	private final int maximoDoRelatorio;

	private final DateTimeFormatter data;

	private final DateTimeFormatter hora;

	public ConsultaDoHistorico(JdbcTemplate jdbc, FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.fusoDaEscola = propriedades.escola().fuso();
		this.tamanhoDaPagina = propriedades.historico().tamanhoPagina();
		this.maximoDoRelatorio = propriedades.relatorio().maxRegistros();
		this.data = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(fusoDaEscola);
		this.hora = DateTimeFormatter.ofPattern("HH:mm").withZone(fusoDaEscola);
	}

	/** Todas as turmas, para o filtro: também as encerradas, que podem ter histórico. */
	public List<Turma> turmas() {
		return jdbc.query("SELECT id_turma, nome, turno, ano_letivo FROM turma ORDER BY ano_letivo DESC, nome, id_turma",
				(rs, linha) -> new Turma(rs.getLong("id_turma"), Liberacao.turmaComTurno(rs.getString("nome"),
						rs.getString("turno")) + " (" + rs.getInt("ano_letivo") + ")"));
	}

	/**
	 * @param pagina a página pedida; fora do intervalo, vale a mais próxima
	 */
	public Pagina consultar(Filtros filtros, int pagina) {
		List<Object> valores = valores(filtros);
		long total = jdbc.queryForObject(CONTAGEM, Long.class, valores.toArray());
		int totalDePaginas = (int) Math.max(1, (total + tamanhoDaPagina - 1) / tamanhoDaPagina);
		int atual = Math.clamp(pagina, 1, totalDePaginas);

		valores.add(tamanhoDaPagina);
		valores.add((atual - 1) * tamanhoDaPagina);
		List<Linha> linhas = jdbc.query(PAGINA, this::linha, valores.toArray());
		return new Pagina(linhas, total, atual, totalDePaginas);
	}

	/**
	 * RF20, passo 2 (DEC-28, C6): as mesmas linhas da consulta, todas de uma vez, até
	 * {@code furin.relatorio.max-registros}.
	 */
	public Relatorio paraImpressao(Filtros filtros) {
		List<Object> valores = valores(filtros);
		long total = jdbc.queryForObject(CONTAGEM, Long.class, valores.toArray());
		if (total > maximoDoRelatorio) {
			return new Relatorio(List.of(), total, true);
		}
		valores.add(maximoDoRelatorio);
		valores.add(0);
		return new Relatorio(jdbc.query(PAGINA, this::linha, valores.toArray()), total, false);
	}

	/** RF20, dados utilizados: o emissor é o nome do funcionário logado. */
	public String nomeDoUsuario(String login) {
		return jdbc.queryForList("SELECT nome FROM usuario WHERE login = ?", String.class, login).stream().findFirst()
				.orElse(login);
	}

	public int maximoDoRelatorio() {
		return maximoDoRelatorio;
	}

	/** Os parâmetros na ordem de {@link #ORIGEM}; um filtro vazio vai como {@code NULL}. */
	private List<Object> valores(Filtros filtros) {
		List<Object> valores = new ArrayList<>();
		// RN10: o dia local inteiro, de 00:00 até o começo do dia seguinte, em UTC
		valores.add(utc(filtros.inicio().atStartOfDay(fusoDaEscola).toInstant()));
		valores.add(utc(filtros.fim().plusDays(1).atStartOfDay(fusoDaEscola).toInstant()));
		valores.add(filtros.idTurma());
		valores.add(filtros.idTurma());
		String aluno = filtros.aluno();
		valores.add(aluno);
		valores.add(aluno == null ? null : "%" + escapar(aluno) + "%");
		valores.add(aluno);
		return valores;
	}

	private Linha linha(ResultSet rs, int numero) throws SQLException {
		Instant ocorrido = instante(rs, "ocorrido_em");
		boolean manual = "MANUAL".equals(rs.getString("forma"));
		return new Linha(data.format(ocorrido), hora.format(ocorrido),
				rs.getString("nome") + " (" + rs.getString("matricula") + ")",
				Liberacao.turmaComTurno(rs.getString("turma"), rs.getString("turno")),
				"SAIDA".equals(rs.getString("tipo")) ? "Saída" : "Entrada", manual ? "Manual" : "Leitura",
				manual ? observacao(rs, ocorrido) : null, aviso(rs.getString("aviso")));
	}

	/**
	 * RF19, passo 2 (DEC-20): o motivo, o horário do lançamento e o autor. A data do
	 * lançamento só aparece quando é outro dia. O texto vai para a tela com {@code th:text}
	 * (CA-RF19-09). [PROP: texto]
	 */
	private String observacao(ResultSet rs, Instant ocorrido) throws SQLException {
		Instant lancado = instante(rs, "lancado_em");
		String motivo = rs.getString("motivo_manual").strip();
		String pontuado = motivo.matches(".*[.!?]$") ? motivo : motivo + ".";
		String quando = data.format(lancado).equals(data.format(ocorrido)) ? "às " + hora.format(lancado)
				: "em " + data.format(lancado) + " às " + hora.format(lancado);
		return pontuado + " Lançado " + quando + " por " + rs.getString("autor") + ".";
	}

	/** RF19, passo 1; RF21, passo 7 (DEC-28, F2). Sem notificação (aviso desligado): "Não enviado". */
	static String aviso(String status) {
		if (status == null) {
			return "Não enviado";
		}
		return switch (status) {
			case "SIMULADA" -> "Simulada";
			case "PENDENTE" -> "Pendente";
			case "FALHA" -> "Falha";
			case "ENVIADA" -> "Enviada";
			default -> status;
		};
	}


	/** A10 §8.28: {@code %}, {@code _} e o próprio escape viram texto no LIKE. */
	static String escapar(String termo) {
		StringBuilder escapado = new StringBuilder(termo.length());
		for (char c : termo.toCharArray()) {
			if (c == '%' || c == '_' || c == ESCAPE) {
				escapado.append(ESCAPE);
			}
			escapado.append(c);
		}
		return escapado.toString();
	}

	private static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
	}

	private static Instant instante(ResultSet rs, String coluna) throws SQLException {
		return rs.getObject(coluna, LocalDateTime.class).toInstant(ZoneOffset.UTC);
	}

}
