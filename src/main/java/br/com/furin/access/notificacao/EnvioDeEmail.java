package br.com.furin.access.notificacao;

import br.com.furin.access.notificacao.MensagemDaNotificacao.Mensagem;

/**
 * O serviço que entrega o e-mail ao responsável (T-39; RF21, passo 6; arquitetura §3.3). No
 * MVP só existe {@link EnvioSimulado}, que não envia nada (DEC-32). Um envio real (perfil
 * {@code smtp}, Spring Mail com TLS) seria outra implementação, fora do projeto.
 */
public interface EnvioDeEmail {

	/** Como terminou uma entrega que não falhou; vira o {@code status} da notificação. */
	enum Resultado {
		/** Nada saiu: protótipo (DEC-32). */
		SIMULADA,
		/** Entregue ao servidor de e-mail. Só numa evolução com envio real. */
		ENVIADA
	}

	/**
	 * @throws RuntimeException se a entrega falhar; a notificação vira {@code FALHA} e é
	 *                          tentada de novo até {@code furin.notificacao.max-tentativas}
	 */
	Resultado enviar(Mensagem mensagem);

}
