package br.com.furin.access.portaria;

import java.time.LocalDate;

/**
 * Leitura recusada (RN09). Por ser uma {@code RuntimeException}, desfaz a transação da
 * leitura: nada é gravado.
 */
public class LeituraRecusada extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final MotivoDaRecusa motivo;

	/** Só em {@link MotivoDaRecusa#SAIDA_PENDENTE}: o dia da entrada sem saída (RN06). */
	private final transient LocalDate saidaPendenteDe;

	LeituraRecusada(MotivoDaRecusa motivo, LocalDate saidaPendenteDe) {
		super(motivo.name(), null, false, false);
		this.motivo = motivo;
		this.saidaPendenteDe = saidaPendenteDe;
	}

	public MotivoDaRecusa getMotivo() {
		return motivo;
	}

	public LocalDate getSaidaPendenteDe() {
		return saidaPendenteDe;
	}

}
