package br.com.furin.access.portaria;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Dados fictícios para os testes da portaria (T-26, T-28), gravados direto no banco:
 * funcionário, turma, aluno com vínculo vigente e carteirinha. Os horários vão em UTC
 * (RN10). Só dados fictícios ("Ana Teste", A10 §8.33).
 */
final class CenaDaPortaria {

	private final JdbcTemplate jdbc;

	CenaDaPortaria(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	long funcionario(String login, Instant agora) {
		// O hash não importa aqui: os testes entram com o usuário de teste do Spring Security
		return inserir("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES (?, ?, '{noop}nao-usada', ?, 1, 0, NULL, ?)",
				"Funcionário Teste", login, SegurancaConfig.PERFIL_FUNCIONARIO, utc(agora));
	}

	long turma(String nome) {
		return inserir("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", nome);
	}

	long aluno(String matricula, String nome, boolean ativo, Instant agora) {
		return inserir("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, ?, ?, ?, ?)",
				matricula, nome, UUID.randomUUID() + ".png", ativo, utc(agora));
	}

	long vinculo(long aluno, long turma, Instant inicio, Instant fim) {
		return inserir("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em, fim_em) VALUES (?, ?, ?, ?)",
				aluno, turma, utc(inicio), fim == null ? null : utc(fim));
	}

	long carteirinha(long aluno, String codigo, Instant emitida, Instant expira, Instant revogada) {
		return inserir("INSERT INTO carteirinha (id_aluno, codigo, emitida_em, expira_em, revogada_em) "
				+ "VALUES (?, ?, ?, ?, ?)", aluno, codigo, utc(emitida), expira == null ? null : utc(expira),
				revogada == null ? null : utc(revogada));
	}

	/** Uma movimentação já confirmada (para a RN01), como se tivesse havido giro. */
	long confirmada(long vinculo, long usuario, long carteirinha, TipoMovimentacao tipo, Instant ocorrido) {
		return inserir("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, ?, ?, ?, 'LEITURA', 'CONFIRMADA', ?, ?, ?)",
				vinculo, usuario, carteirinha, tipo.name(), utc(ocorrido), utc(ocorrido), UUID.randomUUID().toString());
	}

	/** Uma liberação cancelada, que não conta para a RN01. */
	long cancelada(long vinculo, long usuario, long carteirinha, TipoMovimentacao tipo, Instant lancado) {
		return inserir("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, cancelado_em, motivo_cancelamento, chave_requisicao) "
				+ "VALUES (?, ?, ?, ?, 'LEITURA', 'CANCELADA', ?, ?, 'SEM_GIRO', ?)",
				vinculo, usuario, carteirinha, tipo.name(), utc(lancado), utc(lancado), UUID.randomUUID().toString());
	}

	/** Uma liberação AGUARDANDO_GIRO gravada direto, sem passar pela leitura (CA-RF15-08). */
	long pendente(long vinculo, long usuario, long carteirinha, TipoMovimentacao tipo, Instant lancado) {
		return inserir("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, chave_requisicao) VALUES (?, ?, ?, ?, 'LEITURA', 'AGUARDANDO_GIRO', ?, ?)",
				vinculo, usuario, carteirinha, tipo.name(), utc(lancado), UUID.randomUUID().toString());
	}

	/**
	 * CA-RF15-07: quantas movimentações {@code CONFIRMADA} repetem o tipo da anterior do
	 * mesmo aluno, no que esta conexão enxerga do banco. Deve ser sempre zero.
	 */
	int confirmadasRepetidas() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT r.tipo, LAG(r.tipo) OVER ("
				+ "PARTITION BY v.id_aluno ORDER BY r.ocorrido_em, r.id_registro) AS anterior "
				+ "FROM registro_acesso r JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "WHERE r.situacao = 'CONFIRMADA') sequencia WHERE tipo = anterior", Integer.class);
	}

	static LocalDateTime utc(Instant instante) {
		return LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
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

}
