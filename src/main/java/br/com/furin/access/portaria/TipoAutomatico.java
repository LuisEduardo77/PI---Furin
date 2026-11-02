package br.com.furin.access.portaria;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.furin.access.config.RelogioConfig;

/**
 * Tipo automático da movimentação (T-25; RN01; RF13). O tipo <b>nunca</b> vem da
 * interface: este serviço decide a partir da última movimentação {@code CONFIRMADA} do
 * aluno, em qualquer data. Liberações pendentes ou canceladas não entram aqui (quem
 * chama passa só a última confirmada).
 *
 * <table>
 * <caption>Tabela de decisão da RN01</caption>
 * <tr><th>Última confirmada</th><th>Resultado</th></tr>
 * <tr><td>Nenhuma, ou {@code SAIDA}</td><td>{@code ENTRADA}</td></tr>
 * <tr><td>{@code ENTRADA} no mesmo dia</td><td>{@code SAIDA}</td></tr>
 * <tr><td>{@code ENTRADA} em dia anterior</td><td>Recusa: saída pendente (RN06)</td></tr>
 * </table>
 * O dia é o do fuso da escola, não o de UTC (RN10; CA-RF13-05).
 */
@Component
public class TipoAutomatico {

	/** A última movimentação confirmada do aluno: o tipo e o instante do giro. */
	public record UltimaMovimentacao(TipoMovimentacao tipo, Instant ocorridoEm) {
	}

	/**
	 * O resultado: o tipo, ou a recusa por saída pendente com o dia da entrada que ficou
	 * sem saída (para a mensagem "Saída pendente de DD/MM", RN06).
	 */
	public record Decisao(TipoMovimentacao tipo, LocalDate saidaPendenteDe) {

		static Decisao tipo(TipoMovimentacao tipo) {
			return new Decisao(tipo, null);
		}

		static Decisao saidaPendente(LocalDate dia) {
			return new Decisao(null, dia);
		}

		public boolean recusada() {
			return saidaPendenteDe != null;
		}
	}

	private final Clock relogio;

	public TipoAutomatico(Clock relogio) {
		this.relogio = relogio;
	}

	public Decisao decidir(Optional<UltimaMovimentacao> ultimaConfirmada) {
		if (ultimaConfirmada.isEmpty() || ultimaConfirmada.get().tipo() == TipoMovimentacao.SAIDA) {
			return Decisao.tipo(TipoMovimentacao.ENTRADA);
		}
		LocalDate diaDaEntrada = diaNaEscola(ultimaConfirmada.get().ocorridoEm());
		LocalDate hoje = diaNaEscola(relogio.instant());
		return diaDaEntrada.equals(hoje) ? Decisao.tipo(TipoMovimentacao.SAIDA) : Decisao.saidaPendente(diaDaEntrada);
	}

	private static LocalDate diaNaEscola(Instant instante) {
		return LocalDate.ofInstant(instante, RelogioConfig.FUSO_DA_ESCOLA);
	}

}
