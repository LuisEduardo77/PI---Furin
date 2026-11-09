package br.com.furin.access.notificacao;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.notificacao.EnvioDeEmail.Resultado;
import br.com.furin.access.notificacao.MensagemDaNotificacao.Mensagem;
import br.com.furin.access.notificacao.Notificacoes.NotificacoesCriadas;

/**
 * Envio das notificações depois do commit da movimentação (T-39; RF21, passos 4 a 6 e 8;
 * RN11, itens 2 e 6; A10 §8.26).
 * <ul>
 * <li>Recebe {@link NotificacoesCriadas} só depois do commit
 * ({@link TransactionPhase#AFTER_COMMIT}) e trabalha em outra thread ({@link Async}): a
 * leitura e o giro não esperam o envio (CA-RF21-07). Se a confirmação for desfeita, nada é
 * enviado.</li>
 * <li>Cada tentativa soma 1 em {@code tentativas} e grava {@code ultima_tentativa_em}. Uma
 * entrega que não falha deixa o {@code status} que o {@link EnvioDeEmail} devolveu
 * ({@code SIMULADA} no protótipo); uma falha deixa {@code FALHA}, e a notificação é tentada
 * de novo, em seguida, até {@code furin.notificacao.max-tentativas} (DEC-28, F1).</li>
 * <li>Só a tabela {@code notificacao} muda: a movimentação continua {@code CONFIRMADA}
 * (CA-RF21-06).</li>
 * <li>O log recebe {@code NOTIFICACAO_SIMULADA}, {@code NOTIFICACAO_ENVIADA} ou
 * {@code NOTIFICACAO_FALHA} com os IDs e o e-mail mascarado, nunca o corpo (CA-RF21-10).</li>
 * </ul>
 * Sem nenhum {@link EnvioDeEmail} (fora do perfil {@code prototipo}), nada é enviado e a
 * notificação continua {@code PENDENTE}.
 */
@Component
@EnableAsync
public class EnvioDeNotificacoes {

	private static final String NOTIFICACAO = "notificacao";
	private static final String DESTINATARIO = "destinatario";

	private final RegistrosDeNotificacoes registros;

	private final MensagemDaNotificacao mensagens;

	private final ObjectProvider<EnvioDeEmail> envio;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final int maxTentativas;

	EnvioDeNotificacoes(RegistrosDeNotificacoes registros, MensagemDaNotificacao mensagens,
			ObjectProvider<EnvioDeEmail> envio, Auditoria auditoria, Clock relogio, FurinProperties propriedades) {
		this.registros = registros;
		this.mensagens = mensagens;
		this.envio = envio;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.maxTentativas = propriedades.notificacao().maxTentativas();
	}

	/** Depois do commit, em outra thread: envia as notificações da movimentação. */
	@Async("applicationTaskExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void aoCriar(NotificacoesCriadas criadas) {
		for (long idNotificacao : registros.pendentesDoRegistro(criadas.idRegistro())) {
			enviar(idNotificacao);
		}
	}

	/**
	 * Tenta entregar uma notificação {@code PENDENTE} ou em {@code FALHA}, até o limite de
	 * tentativas. Nenhuma falha do serviço de envio sai daqui.
	 */
	public void enviar(long idNotificacao) {
		EnvioDeEmail servico = envio.getIfAvailable();
		if (servico == null) {
			return;
		}
		Optional<Mensagem> montada = mensagens.montar(idNotificacao);
		if (montada.isEmpty()) {
			return;
		}
		Mensagem mensagem = montada.get();
		// Cada volta reserva uma tentativa no banco; sem reserva (limite ou já entregue), acaba
		int tentativa = registros.reservarTentativa(idNotificacao, relogio.instant(), maxTentativas);
		while (tentativa > 0 && !tentar(servico, idNotificacao, mensagem, tentativa)) {
			tentativa = registros.reservarTentativa(idNotificacao, relogio.instant(), maxTentativas);
		}
	}

	/** @return se a entrega terminou sem falha */
	private boolean tentar(EnvioDeEmail servico, long idNotificacao, Mensagem mensagem, int tentativa) {
		Resultado resultado;
		try {
			resultado = servico.enviar(mensagem);
		} catch (RuntimeException falha) {
			registros.marcar(idNotificacao, "FALHA");
			// Só o tipo da falha: a mensagem da exceção pode trazer o endereço ou o corpo
			auditoria.registrar(EventoAuditoria.NOTIFICACAO_FALHA, detalhes().id(NOTIFICACAO, idNotificacao)
					.email(DESTINATARIO, mensagem.destinatario()).id("tentativa", tentativa)
					.texto("falha", falha.getClass().getSimpleName()));
			return false;
		}
		registros.marcar(idNotificacao, resultado.name());
		auditoria.registrar(resultado == Resultado.SIMULADA ? EventoAuditoria.NOTIFICACAO_SIMULADA
				: EventoAuditoria.NOTIFICACAO_ENVIADA,
				detalhes().id(NOTIFICACAO, idNotificacao).email(DESTINATARIO, mensagem.destinatario()));
		return true;
	}

}
