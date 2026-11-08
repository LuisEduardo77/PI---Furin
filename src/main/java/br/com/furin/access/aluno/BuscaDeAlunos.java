package br.com.furin.access.aluno;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.Liberacao;

/**
 * Busca de aluno por nome ou matrícula (T-24; RF11; DEC-28, C4), para a tela Alunos e para o
 * registro manual da portaria (T-32, com {@link Escopo#SO_ATIVOS}).
 * <ul>
 * <li>Só dígitos: matrícula igual ou que começa pelo termo, com os zeros à esquerda;</li>
 * <li>o resto: nome que contém o termo, sem diferenciar maiúsculas nem acentos (collation
 * {@code utf8mb4_0900_ai_ci}), com pelo menos {@code furin.busca.tamanho-minimo-nome}
 * caracteres.</li>
 * </ul>
 * A consulta é parametrizada, e {@code %} e {@code _} viram texto (A10 §8.28). No máximo
 * {@code furin.busca.max-resultados}, em ordem de nome. Sem foto e sem dados do responsável
 * (A10 §5.34).
 */
@Service
public class BuscaDeAlunos {

	/** RF11, escopo dos resultados [DEC-28, C4]. */
	public enum Escopo {
		/** Registro manual na portaria. */
		SO_ATIVOS,
		/** Tela Alunos, com o filtro "Incluir inativos". */
		COM_INATIVOS
	}

	/** RN12; RF11, passo 4: a situação do aluno, em texto. */
	public enum Situacao {
		NA_ESCOLA("Na escola"),
		/** ENTRADA de dia anterior sem saída (RN12, refinamento C1) [PROP: texto]. */
		SAIDA_PENDENTE("Saída pendente"),
		FORA_DA_ESCOLA("Fora da escola"),
		INATIVO("Inativo");

		private final String texto;

		Situacao(String texto) {
			this.texto = texto;
		}

		public String texto() {
			return texto;
		}
	}

	/** Um resultado: só o que a lista mostra. {@code turma} é nula sem vínculo vigente. */
	public record AlunoEncontrado(long id, String nome, String matricula, String turma, Situacao situacao) {
	}

	/** {@code limiteAtingido}: havia mais resultados que {@code furin.busca.max-resultados}. */
	public record Resultado(List<AlunoEncontrado> alunos, boolean limiteAtingido) {
	}

	private static final Pattern SO_DIGITOS = Pattern.compile("\\d+");

	/** O caractere de escape do LIKE; ele mesmo também é escapado. */
	private static final char ESCAPE = '!';

	/** Aluno, turma vigente e a última movimentação confirmada (RN12), sem foto nem contato. */
	private static final String CONSULTA = "SELECT a.id_aluno, a.nome, a.matricula, a.ativo, "
			+ "t.nome AS turma, t.turno, ultima.tipo, ultima.ocorrido_em FROM aluno a "
			+ "LEFT JOIN vinculo_turma v ON v.id_aluno = a.id_aluno AND v.inicio_em <= ? "
			+ "AND (v.fim_em IS NULL OR v.fim_em > ?) "
			+ "LEFT JOIN turma t ON t.id_turma = v.id_turma "
			+ "LEFT JOIN LATERAL (SELECT r.tipo, r.ocorrido_em FROM registro_acesso r "
			+ "JOIN vinculo_turma rv ON rv.id_vinculo = r.id_vinculo "
			+ "WHERE rv.id_aluno = a.id_aluno AND r.situacao = 'CONFIRMADA' "
			+ "ORDER BY r.ocorrido_em DESC, r.id_registro DESC LIMIT 1) ultima ON TRUE "
			+ "WHERE (a.ativo = 1 OR ?) AND ";

	private static final String ORDEM = " LIKE ? ESCAPE '" + ESCAPE + "' ORDER BY a.nome, a.id_aluno LIMIT ?";

	private static final String POR_MATRICULA = CONSULTA + "a.matricula" + ORDEM;

	private static final String POR_NOME = CONSULTA + "a.nome" + ORDEM;

	private final JdbcTemplate jdbc;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	private final int maxResultados;

	private final int tamanhoMinimoNome;

	public BuscaDeAlunos(JdbcTemplate jdbc, Clock relogio, FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
		this.maxResultados = propriedades.busca().maxResultados();
		this.tamanhoMinimoNome = propriedades.busca().tamanhoMinimoNome();
	}

	public int maxResultados() {
		return maxResultados;
	}

	/** RF11, exceções: a mensagem para um termo que não pode ser buscado, ou vazio se ele serve. */
	public Optional<String> problemaNoTermo(String termo) {
		String limpo = termo == null ? "" : termo.strip();
		if (limpo.isEmpty()) {
			return Optional.of("Digite o nome ou a matrícula do aluno.");
		}
		if (!SO_DIGITOS.matcher(limpo).matches() && limpo.codePointCount(0, limpo.length()) < tamanhoMinimoNome) {
			return Optional.of("Digite pelo menos " + tamanhoMinimoNome + " caracteres do nome.");
		}
		return Optional.empty();
	}

	/**
	 * @throws IllegalArgumentException se {@link #problemaNoTermo(String)} recusar o termo
	 */
	public Resultado buscar(String termo, Escopo escopo) {
		if (termo == null || problemaNoTermo(termo).isPresent()) {
			throw new IllegalArgumentException("Termo de busca inválido");
		}
		String limpo = termo.strip();
		boolean porMatricula = SO_DIGITOS.matcher(limpo).matches();
		String padrao = porMatricula ? escapar(limpo) + "%" : "%" + escapar(limpo) + "%";
		LocalDateTime agora = LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);

		List<AlunoEncontrado> encontrados = jdbc.query(porMatricula ? POR_MATRICULA : POR_NOME, this::encontrado,
				agora, agora, escopo == Escopo.COM_INATIVOS, padrao, maxResultados + 1);

		boolean limiteAtingido = encontrados.size() > maxResultados;
		return new Resultado(limiteAtingido ? encontrados.subList(0, maxResultados) : encontrados, limiteAtingido);
	}

	private AlunoEncontrado encontrado(ResultSet rs, int linha) throws SQLException {
		String turma = rs.getString("turma") == null ? null
				: Liberacao.turmaComTurno(rs.getString("turma"), rs.getString("turno"));
		return new AlunoEncontrado(rs.getLong("id_aluno"), rs.getString("nome"), rs.getString("matricula"), turma,
				situacao(rs));
	}

	/** RN12, com o refinamento C1: só a ENTRADA de hoje (no fuso da escola) é "Na escola". */
	private Situacao situacao(ResultSet rs) throws SQLException {
		if (!rs.getBoolean("ativo")) {
			return Situacao.INATIVO;
		}
		if (!"ENTRADA".equals(rs.getString("tipo"))) {
			return Situacao.FORA_DA_ESCOLA;
		}
		LocalDate diaDaEntrada = rs.getObject("ocorrido_em", LocalDateTime.class).atOffset(ZoneOffset.UTC)
				.atZoneSameInstant(fusoDaEscola).toLocalDate();
		// LocalDate.ofInstant, e não relogio.withZone: o relógio dos testes fica sempre em UTC
		return diaDaEntrada.equals(LocalDate.ofInstant(relogio.instant(), fusoDaEscola)) ? Situacao.NA_ESCOLA
				: Situacao.SAIDA_PENDENTE;
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

}
