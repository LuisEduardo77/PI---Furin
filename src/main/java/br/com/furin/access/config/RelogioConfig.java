package br.com.furin.access.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação, em UTC (RN10). Fica num bean para que os testes possam fixar
 * o horário.
 */
@Configuration
public class RelogioConfig {

	@Bean
	Clock relogio() {
		return Clock.systemUTC();
	}

}
