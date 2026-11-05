package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.portaria.TipoAutomatico.Decisao;
import br.com.furin.access.portaria.TipoAutomatico.UltimaMovimentacao;

/**
 * Tabela de decisão da RN01 (T-25; RF13, CA-RF13-01 a 05), com o relógio fixo. Os
 * horários dos casos estão no fuso da escola (America/Sao_Paulo), como na RN10.
 */
class TipoAutomaticoTests {

	/** "Agora" nos casos: 20/10/2026, 12:30 no horário da escola. */
	private static final LocalDateTime AGORA_NA_ESCOLA = LocalDateTime.of(2026, 10, 20, 12, 30);

	private final TipoAutomatico servico = new TipoAutomatico(relogioEm(AGORA_NA_ESCOLA), RelogioAjustavel.FUSO_DA_ESCOLA);

	static Stream<Arguments> tabelaDeDecisao() {
		return Stream.of(
				// CA-RF13-01: nenhuma movimentação
				Arguments.of("nenhuma movimentação", null, null, TipoMovimentacao.ENTRADA),
				// CA-RF13-02: SAIDA de qualquer dia
				Arguments.of("SAIDA hoje", TipoMovimentacao.SAIDA, LocalDateTime.of(2026, 10, 20, 11, 0),
						TipoMovimentacao.ENTRADA),
				Arguments.of("SAIDA em dia anterior", TipoMovimentacao.SAIDA, LocalDateTime.of(2026, 10, 2, 17, 0),
						TipoMovimentacao.ENTRADA),
				// CA-RF13-03: ENTRADA às 07:02 de hoje
				Arguments.of("ENTRADA hoje", TipoMovimentacao.ENTRADA, LocalDateTime.of(2026, 10, 20, 7, 2),
						TipoMovimentacao.SAIDA),
				Arguments.of("ENTRADA hoje à 00:00", TipoMovimentacao.ENTRADA, LocalDateTime.of(2026, 10, 20, 0, 0),
						TipoMovimentacao.SAIDA));
	}

	@ParameterizedTest(name = "{0} → {3}")
	@MethodSource("tabelaDeDecisao")
	void tabelaDeDecisaoDaRn01(String caso, TipoMovimentacao ultimoTipo, LocalDateTime ultimaNaEscola,
			TipoMovimentacao esperado) {
		Optional<UltimaMovimentacao> ultima = ultimoTipo == null ? Optional.empty()
				: Optional.of(new UltimaMovimentacao(ultimoTipo, naEscola(ultimaNaEscola)));

		Decisao decisao = servico.decidir(ultima);

		assertThat(decisao.recusada()).isFalse();
		assertThat(decisao.tipo()).isEqualTo(esperado);
	}

	@Test
	void entradaEmDiaAnteriorRecusaPorSaidaPendente() {
		// CA-RF13-04 (RN06; DEC-28, A5)
		Decisao decisao = servico.decidir(Optional.of(
				new UltimaMovimentacao(TipoMovimentacao.ENTRADA, naEscola(LocalDateTime.of(2026, 10, 19, 7, 2)))));

		assertThat(decisao.recusada()).isTrue();
		assertThat(decisao.tipo()).isNull();
		assertThat(decisao.saidaPendenteDe()).isEqualTo(LocalDate.of(2026, 10, 19));
	}

	@Test
	void viradaDoDiaUsaOFusoDaEscolaENaoUtc() {
		// CA-RF13-05: ENTRADA às 23:50 de 19/10 na escola (já é 20/10 em UTC) e leitura à
		// 00:10 de 20/10 na escola → dia anterior
		TipoAutomatico logoDepoisDaMeiaNoite = new TipoAutomatico(relogioEm(LocalDateTime.of(2026, 10, 20, 0, 10)),
				RelogioAjustavel.FUSO_DA_ESCOLA);
		Instant entrada = naEscola(LocalDateTime.of(2026, 10, 19, 23, 50));
		assertThat(LocalDate.ofInstant(entrada, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2026, 10, 20));

		Decisao decisao = logoDepoisDaMeiaNoite.decidir(
				Optional.of(new UltimaMovimentacao(TipoMovimentacao.ENTRADA, entrada)));

		assertThat(decisao.recusada()).isTrue();
		assertThat(decisao.saidaPendenteDe()).isEqualTo(LocalDate.of(2026, 10, 19));
	}

	@Test
	void entradaDeHojeAntesDaMeiaNoiteEmUtcContinuaSendoHoje() {
		// O contrário da virada: 21:30 na escola já é o dia seguinte em UTC, mas é "hoje"
		TipoAutomatico aNoite = new TipoAutomatico(relogioEm(LocalDateTime.of(2026, 10, 20, 22, 0)),
				RelogioAjustavel.FUSO_DA_ESCOLA);

		Decisao decisao = aNoite.decidir(Optional.of(
				new UltimaMovimentacao(TipoMovimentacao.ENTRADA, naEscola(LocalDateTime.of(2026, 10, 20, 21, 30)))));

		assertThat(decisao.tipo()).isEqualTo(TipoMovimentacao.SAIDA);
	}

	private static Instant naEscola(LocalDateTime horario) {
		return horario.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();
	}

	/** O relógio da aplicação continua em UTC; só o instante é escolhido no horário da escola. */
	private static Clock relogioEm(LocalDateTime horarioNaEscola) {
		return Clock.fixed(naEscola(horarioNaEscola), ZoneOffset.UTC);
	}

}
