package br.com.furin.access.notificacao;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import br.com.furin.access.notificacao.MensagemDaNotificacao.Mensagem;

/**
 * Envio simulado (T-39; DEC-32; RF21, passo 6): nenhum e-mail sai, e a notificação fica
 * {@code SIMULADA}. Só existe no perfil {@code prototipo}, como as outras simulações
 * (DEC-18, DEC-25). Fora dele não há envio, e a notificação continua {@code PENDENTE}.
 */
@Component
@Profile("prototipo")
public class EnvioSimulado implements EnvioDeEmail {

	@Override
	public Resultado enviar(Mensagem mensagem) {
		return Resultado.SIMULADA;
	}

}
