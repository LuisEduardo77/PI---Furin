package br.com.furin.access.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, em UTC (RN10). Fica num bean para que os testes possam fixar
 * o horário.
 */
@Configuration
public class RelogioConfig {

	/**
	 * Fuso da escola (RN10): os horários são gravados em UTC, mas "mesmo dia" (RN01) e
	 * "hoje" (RN12) são calculados neste fuso. É convenção do projeto, não um parâmetro da
	 * tabela de arquitetura §4, por isso fica aqui, num lugar só [PROP].
	 */
	public static final ZoneId FUSO_DA_ESCOLA = ZoneId.of("America/Sao_Paulo");

	@Bean
	Clock relogio() {
		return Clock.systemUTC();
	}

}
