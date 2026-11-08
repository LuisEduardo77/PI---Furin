package br.com.furin.access.notificacao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;

/**
 * O conteúdo do e-mail, montado na hora a partir do registro (T-38; RF21, passo 5; RN11,
 * itens 3 e 7; A10 §5.34). Nada disso fica gravado em {@code notificacao}.
 * <p>
 * Vão só o primeiro nome, o tipo, a data e a hora no fuso da escola (RN10) e a forma
 * ("leitura da carteirinha" ou "registro manual", DEC-28, B3). Não vão foto, turma,
 * matrícula, nome completo nem link para o sistema (DEC-37). Os textos são [PROP]. Quem
 * usa é o envio (T-39, {@link EnvioDeNotificacoes}) e o aviso de simulação da portaria
 * ({@link AvisoDaSimulacao}).
 */
@Component
public class MensagemDaNotificacao {

	/** O e-mail pronto: destinatário, assunto e corpo. */
	public record Mensagem(String destinatario, String assunto, String corpo) {
	}

	private static final String DADOS = "SELECT c.email, a.nome, r.tipo, r.forma, r.ocorrido_em FROM notificacao n "
			+ "JOIN contato_responsavel c ON c.id_contato = n.id_contato "
			+ "JOIN registro_acesso r ON r.id_registro = n.id_registro "
			+ "JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
			+ "JOIN aluno a ON a.id_aluno = v.id_aluno ";

	private final JdbcTemplate jdbc;

	private final DateTimeFormatter dataNaEscola;

	private final DateTimeFormatter horaNaEscola;

	public MensagemDaNotificacao(JdbcTemplate jdbc, FurinProperties propriedades) {
		this.jdbc = jdbc;
		this.dataNaEscola = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(propriedades.escola().fuso());
		this.horaNaEscola = DateTimeFormatter.ofPattern("HH:mm").withZone(propriedades.escola().fuso());
	}

	/** A mensagem de uma notificação; vazia se ela não existe. */
	public Optional<Mensagem> montar(long idNotificacao) {
		return consultar(DADOS + "WHERE n.id_notificacao = ?", idNotificacao);
	}

	/** A mensagem da notificação de uma movimentação (T-39, aviso de simulação); vazia se não há. */
	public Optional<Mensagem> doRegistro(long idRegistro) {
		return consultar(DADOS + "WHERE n.id_registro = ? ORDER BY n.id_notificacao LIMIT 1", idRegistro);
	}

	private Optional<Mensagem> consultar(String sql, long id) {
		return jdbc.query(sql,
				(rs, linha) -> mensagem(rs.getString("email"), rs.getString("nome"), rs.getString("tipo"),
						rs.getString("forma"), rs.getObject("ocorrido_em", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
				id).stream().findFirst();
	}

	private Mensagem mensagem(String email, String nome, String tipo, String forma, Instant ocorrido) {
		boolean entrada = "ENTRADA".equals(tipo);
		String assunto = "Furin Access — " + (entrada ? "entrada" : "saída") + " registrada";
		String corpo = primeiroNome(nome) + (entrada ? " entrou na escola em " : " saiu da escola em ")
				+ dataNaEscola.format(ocorrido) + " às " + horaNaEscola.format(ocorrido)
				+ ("MANUAL".equals(forma) ? " (registro manual)." : " (leitura da carteirinha).") + "\n"
				+ "Em caso de dúvida, procure a secretaria da escola.\n"
				+ "Para deixar de receber estes avisos, peça à escola.";
		return new Mensagem(email, assunto, corpo);
	}

	private static String primeiroNome(String nome) {
		String limpo = nome.strip();
		int espaco = limpo.indexOf(' ');
		return espaco < 0 ? limpo : limpo.substring(0, espaco);
	}

}
