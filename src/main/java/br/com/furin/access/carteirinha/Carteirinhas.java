package br.com.furin.access.carteirinha;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.portaria.Liberacao;

/**
 * Impressão e segunda via da carteirinha (T-22; RF07; RN15). A emissão no cadastro fica em
 * {@code CadastroDeAlunos}, com {@link EmissaoDeCarteirinha}, na transação do RF04.
 */
@Service
public class Carteirinhas {

	/**
	 * O que a página de impressão precisa. O {@code codigo} só vai para o QR Code, nunca para o
	 * texto da página (DEC-28, D8). {@code turma} é nula sem vínculo vigente.
	 */
	public record ParaImprimir(long id, long idAluno, String nomeDoAluno, String turma, String codigo,
			boolean revogada) {
	}

	/** O aluno da segunda via está inativo (RF07, exceções). */
	public static class AlunoInativo extends RuntimeException {

		private static final long serialVersionUID = 1L;

		AlunoInativo() {
			super("Aluno inativo", null, false, false);
		}
	}

	private final JdbcTemplate jdbc;

	private final EmissaoDeCarteirinha emissao;

	private final Auditoria auditoria;

	private final Clock relogio;

	public Carteirinhas(JdbcTemplate jdbc, EmissaoDeCarteirinha emissao, Auditoria auditoria, Clock relogio) {
		this.jdbc = jdbc;
		this.emissao = emissao;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	public Optional<ParaImprimir> paraImprimir(long idCarteirinha) {
		LocalDateTime agora = LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
		return jdbc.query("SELECT c.id_carteirinha, a.id_aluno, a.nome, c.codigo, c.revogada_em, t.nome AS turma, "
				+ "t.turno FROM carteirinha c JOIN aluno a ON a.id_aluno = c.id_aluno "
				+ "LEFT JOIN vinculo_turma v ON v.id_aluno = a.id_aluno AND v.inicio_em <= ? "
				+ "AND (v.fim_em IS NULL OR v.fim_em > ?) LEFT JOIN turma t ON t.id_turma = v.id_turma "
				+ "WHERE c.id_carteirinha = ? ORDER BY v.inicio_em DESC LIMIT 1",
				(rs, linha) -> new ParaImprimir(rs.getLong("id_carteirinha"), rs.getLong("id_aluno"),
						rs.getString("nome"),
						rs.getString("turma") == null ? null
								: Liberacao.turmaComTurno(rs.getString("turma"), rs.getString("turno")),
						rs.getString("codigo"), rs.getObject("revogada_em") != null),
				agora, agora, idCarteirinha).stream().findFirst();
	}

	/** O nome do aluno, para a tela de confirmação da segunda via. */
	public Optional<String> nomeDoAluno(long idAluno) {
		return jdbc.queryForList("SELECT nome FROM aluno WHERE id_aluno = ?", String.class, idAluno).stream()
				.findFirst();
	}

	/** A carteirinha válida do aluno, se houver (RN15: no máximo uma). */
	public Optional<Long> valida(long idAluno) {
		return jdbc.queryForList("SELECT id_carteirinha FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				Long.class, idAluno).stream().findFirst();
	}

	/**
	 * RF07, segunda via, passo 3: com o aluno bloqueado (RN08), revoga a carteirinha atual e
	 * grava uma nova, com código novo. Os códigos revogados nunca voltam (RN15).
	 *
	 * @return o ID da carteirinha nova
	 * @throws AlunoInativo se o aluno estiver inativo; nada muda
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public long emitirSegundaVia(long idAluno) {
		Boolean ativo = jdbc.queryForObject("SELECT ativo FROM aluno WHERE id_aluno = ? FOR UPDATE", Boolean.class,
				idAluno);
		if (!Boolean.TRUE.equals(ativo)) {
			throw new AlunoInativo();
		}
		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		jdbc.update("UPDATE carteirinha SET revogada_em = ? WHERE id_aluno = ? AND revogada_em IS NULL",
				LocalDateTime.ofInstant(agora, ZoneOffset.UTC), idAluno);
		long nova = emissao.emitir(idAluno, agora);
		// Só os IDs: o código nunca vai para o log
		auditoria.registrar(EventoAuditoria.CARTEIRINHA_SEGUNDA_VIA,
				detalhes().id("aluno", idAluno).id("carteirinha", nova));
		return nova;
	}

}
