package br.com.furin.access.arquivo;

/** Arquivo fora das regras de {@code furin.arquivos.*}. Nada foi gravado. */
public class ArquivoRecusado extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Por que o arquivo foi recusado. Os textos para a tela vêm com os formulários (T-19, T-20). */
	public enum Motivo {
		/** Maior que {@code tamanho-max}. */
		TAMANHO_EXCEDIDO,
		/** O conteúdo não é de um dos {@code tipos} aceitos. */
		TIPO_NAO_ACEITO
	}

	private final Motivo motivo;

	ArquivoRecusado(Motivo motivo) {
		super(motivo.name(), null, false, false);
		this.motivo = motivo;
	}

	public Motivo getMotivo() {
		return motivo;
	}

}
