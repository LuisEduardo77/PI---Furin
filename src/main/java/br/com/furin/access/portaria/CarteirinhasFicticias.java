package br.com.furin.access.portaria;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Carteirinhas do painel "Simular leitura (protótipo)" (T-30; DEC-25; arquitetura §3.1):
 * as válidas de alunos ativos com turma vigente. Só existe no perfil {@code prototipo},
 * onde todos os dados são fictícios (carga da T-11). Fora dele, não há painel nem esta
 * consulta.
 */
@Component
@Profile("prototipo")
public class CarteirinhasFicticias {

	/** Um botão do painel: quem é e o código que o botão envia. */
	public record CarteirinhaParaSimular(String nome, String turma, String codigo) {
	}

	private final JdbcTemplate jdbc;

	private final Clock relogio;

	public CarteirinhasFicticias(JdbcTemplate jdbc, Clock relogio) {
		this.jdbc = jdbc;
		this.relogio = relogio;
	}

	public List<CarteirinhaParaSimular> ativas() {
		LocalDateTime agora = LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
		return jdbc.query("SELECT a.nome, t.nome AS turma, c.codigo FROM carteirinha c "
				+ "JOIN aluno a ON a.id_aluno = c.id_aluno "
				+ "JOIN vinculo_turma v ON v.id_aluno = a.id_aluno "
				+ "JOIN turma t ON t.id_turma = v.id_turma "
				+ "WHERE a.ativo = 1 AND c.revogada_em IS NULL AND (c.expira_em IS NULL OR c.expira_em > ?) "
				+ "AND v.inicio_em <= ? AND (v.fim_em IS NULL OR v.fim_em > ?) "
				+ "ORDER BY a.nome, c.id_carteirinha",
				(rs, linha) -> new CarteirinhaParaSimular(rs.getString("nome"), rs.getString("turma"),
						rs.getString("codigo")),
				agora, agora, agora);
	}

}
