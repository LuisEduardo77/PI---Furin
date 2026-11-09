package br.com.furin.access.notificacao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Criação da notificação ao contato principal (T-38; RF21, passos 1 a 3; RN11, itens 1, 5
 * e 7). É chamada pelo giro ({@code portaria.Giros}) e pelo registro manual
 * ({@code portaria.RegistroManual}) logo depois de gravar a movimentação
 * {@code CONFIRMADA}, na mesma transação ({@link Propagation#MANDATORY}): se a confirmação
 * for desfeita, a notificação também é.
 * <p>
 * A notificação nasce {@code PENDENTE}, {@code EMAIL}, com 0 tentativas, só quando o
 * contato principal do aluno está ativo, tem e-mail e está com
 * {@code recebe_notificacao = 1}. Uma liberação {@code AGUARDANDO_GIRO} ou
 * {@code CANCELADA} nunca gera notificação, e o banco garante uma só por movimentação e
 * contato ({@code uk_notificacao}). A tabela não guarda o corpo da mensagem (A10 §5.34):
 * ele é montado na hora do envio por {@link MensagemDaNotificacao}. O envio (T-39) fica com
 * o {@link EnvioDeNotificacoes}, que recebe o evento {@link NotificacoesCriadas} depois do
 * commit.
 * <p>
 * Um contato principal sem e-mail não chega a existir: o banco recusa
 * ({@code ck_contato_principal}). A consulta exige o e-mail mesmo assim.
 */
@Service
public class Notificacoes {

	/**
	 * Avisa o envio (T-39) de que a movimentação {@code idRegistro} criou notificações. O
	 * {@link EnvioDeNotificacoes} só o recebe depois do commit.
	 */
	public record NotificacoesCriadas(long idRegistro) {
	}

	private final JdbcTemplate jdbc;

	private final ApplicationEventPublisher eventos;

	public Notificacoes(JdbcTemplate jdbc, ApplicationEventPublisher eventos) {
		this.jdbc = jdbc;
		this.eventos = eventos;
	}

	/**
	 * @param idRegistro a movimentação que acabou de ser confirmada
	 * @param agora      instante do servidor, gravado em {@code criado_em} (UTC, RN10)
	 * @return quantas notificações foram criadas (0 ou 1)
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public int criarParaConfirmada(long idRegistro, Instant agora) {
		int criadas = inserir(idRegistro, agora);
		if (criadas > 0) {
			// T-39: o envio só acontece depois do commit, fora do caminho da leitura e do giro
			eventos.publishEvent(new NotificacoesCriadas(idRegistro));
		}
		return criadas;
	}

	private int inserir(long idRegistro, Instant agora) {
		return jdbc.update("INSERT INTO notificacao (id_registro, id_contato, canal, status, tentativas, criado_em) "
				+ "SELECT r.id_registro, c.id_contato, 'EMAIL', 'PENDENTE', 0, ? FROM registro_acesso r "
				+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "JOIN contato_responsavel c ON c.id_aluno = v.id_aluno AND c.principal = 1 AND c.ativo = 1 "
				+ "AND c.recebe_notificacao = 1 AND COALESCE(TRIM(c.email), '') <> '' "
				+ "WHERE r.id_registro = ? AND r.situacao = 'CONFIRMADA'",
				LocalDateTime.ofInstant(agora, ZoneOffset.UTC), idRegistro);
	}

}
