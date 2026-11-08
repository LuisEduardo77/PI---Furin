package br.com.furin.access.notificacao;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import br.com.furin.access.auditoria.Mascaramento;

/**
 * O aviso de simulação que a portaria mostra depois da confirmação (T-39; RF21, passo 6;
 * DEC-28, F3; DEC-32): "Simulação — nenhum e-mail foi enviado", o destinatário mascarado e o
 * texto que o e-mail teria, montado como no envio ({@link MensagemDaNotificacao}). Só
 * existe com o {@link EnvioSimulado} (perfil {@code prototipo}) e só quando a movimentação
 * gerou notificação.
 */
@Component
public class AvisoDaSimulacao {

	/** O texto fixo do aviso, igual na tela do giro e na do registro manual. [PROP] */
	public static final String TITULO = "Simulação — nenhum e-mail foi enviado";

	/**
	 * @param destinatario sempre mascarado ({@code c***@example.com}), como no log
	 */
	public record Aviso(String titulo, String destinatario, String assunto, String corpo) {
	}

	private final MensagemDaNotificacao mensagens;

	private final ObjectProvider<EnvioSimulado> simulado;

	public AvisoDaSimulacao(MensagemDaNotificacao mensagens, ObjectProvider<EnvioSimulado> simulado) {
		this.mensagens = mensagens;
		this.simulado = simulado;
	}

	/** O aviso da movimentação; vazio fora do protótipo ou sem notificação (contato com o aviso desligado). */
	public Optional<Aviso> doRegistro(long idRegistro) {
		if (simulado.getIfAvailable() == null) {
			return Optional.empty();
		}
		return mensagens.doRegistro(idRegistro).map(mensagem -> new Aviso(TITULO,
				Mascaramento.email(mensagem.destinatario()), mensagem.assunto(), mensagem.corpo()));
	}

}
