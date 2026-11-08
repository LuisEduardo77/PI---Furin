package br.com.furin.access.portaria;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * A tarefa agendada da expiração (T-29; RF09, fluxo A1, passo 2; arquitetura §3.1): a
 * cada {@code furin.portaria.verificacao-expiracao}, cancela as liberações sem giro.
 * Sem ela, a liberação vencida só seria cancelada na próxima operação da portaria, e a
 * tela não saberia que ela expirou.
 * <p>
 * {@code furin.agendamentos.ativos=false} a desliga. O perfil {@code test} faz isso: cada
 * contexto de teste guardado pelo Spring teria a sua tarefa, com o próprio relógio.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "furin.agendamentos.ativos", havingValue = "true", matchIfMissing = true)
public class ExpiracaoAgendada {

	private final Expiracoes expiracoes;

	public ExpiracaoAgendada(Expiracoes expiracoes) {
		this.expiracoes = expiracoes;
	}

	@Scheduled(fixedDelayString = "${furin.portaria.verificacao-expiracao}")
	public void expirar() {
		expiracoes.expirarVencidas();
	}

}
