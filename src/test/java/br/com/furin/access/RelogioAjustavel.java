package br.com.furin.access;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Relógio em UTC que só anda quando o teste manda (T-14). Entra no lugar do bean
 * {@code relogio} (RelogioConfig) com {@code @TestBean(name = "relogio")}.
 */
public final class RelogioAjustavel extends Clock {

	/** O valor de {@code furin.escola.fuso}, para os testes escreverem horários da escola (RN10). */
	public static final ZoneId FUSO_DA_ESCOLA = ZoneId.of("America/Sao_Paulo");

	private volatile Instant agora;

	public RelogioAjustavel(Instant inicio) {
		this.agora = inicio;
	}

	public void avancar(Duration tempo) {
		agora = agora.plus(tempo);
	}

	@Override
	public Instant instant() {
		return agora;
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zona) {
		throw new UnsupportedOperationException("O relógio do teste fica sempre em UTC");
	}

}
