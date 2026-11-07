package br.com.furin.access.carteirinha;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;

/**
 * Grava a carteirinha com um código que nunca foi usado (T-21; RF06, código da carteirinha;
 * RN15). Roda na transação de quem chama: o cadastro (T-22) ou a segunda via.
 * <p>
 * O código é conferido em <b>todas</b> as carteirinhas, inclusive as revogadas. Numa
 * colisão, gera outro, até {@code furin.carteirinha.tentativas-colisao} novas tentativas
 * (DEC-28, D7). A restrição {@code uk_carteirinha_codigo} garante a unicidade mesmo com
 * duas emissões ao mesmo tempo: o código barrado por ela também conta como colisão.
 */
@Service
public class EmissaoDeCarteirinha {

	/** Todas as tentativas colidiram. Quem chama desfaz a transação. */
	public static class CarteirinhaNaoEmitida extends RuntimeException {

		private static final long serialVersionUID = 1L;

		CarteirinhaNaoEmitida() {
			super("Não foi possível emitir a carteirinha", null, false, false);
		}
	}

	private final JdbcTemplate jdbc;

	private final GeradorDeCodigo gerador;

	private final Auditoria auditoria;

	private final int novasTentativas;

	public EmissaoDeCarteirinha(JdbcTemplate jdbc, GeradorDeCodigo gerador, Auditoria auditoria,
			FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.gerador = gerador;
		this.auditoria = auditoria;
		this.novasTentativas = propriedades.carteirinha().tentativasColisao();
	}

	/**
	 * @return o ID da carteirinha gravada
	 * @throws CarteirinhaNaoEmitida se o código colidiu na primeira tentativa e em todas as novas
	 */
	public long emitir(long idAluno, Instant emitidaEm) {
		for (int tentativa = 0; tentativa <= novasTentativas; tentativa++) {
			String codigo = gerador.gerar();
			if (codigoUsado(codigo)) {
				continue;
			}
			try {
				return gravar(idAluno, codigo, emitidaEm);
			} catch (DuplicateKeyException outraEmissaoAoMesmoTempo) {
				// Outra emissão gravou o mesmo código depois da conferência: tenta outro
			}
		}
		// RF06, exceções: só o ID do aluno, nunca o código
		auditoria.registrar(EventoAuditoria.CARTEIRINHA_NAO_EMITIDA, detalhes().id("aluno", idAluno));
		throw new CarteirinhaNaoEmitida();
	}

	private boolean codigoUsado(String codigo) {
		Integer quantidade = jdbc.queryForObject("SELECT COUNT(*) FROM carteirinha WHERE codigo = ?", Integer.class,
				codigo);
		return quantidade != null && quantidade > 0;
	}

	private long gravar(long idAluno, String codigo, Instant emitidaEm) {
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em, expira_em, revogada_em) "
				+ "VALUES (?, ?, ?, NULL, NULL)", idAluno, codigo, LocalDateTime.ofInstant(emitidaEm, ZoneOffset.UTC));
		return jdbc.queryForObject("SELECT id_carteirinha FROM carteirinha WHERE codigo = ?", Long.class, codigo);
	}

}
