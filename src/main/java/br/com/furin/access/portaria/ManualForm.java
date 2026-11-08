package br.com.furin.access.portaria;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O formulário do registro manual (T-32; RF12, passos 4 e 5). Não existe campo de tipo para
 * o funcionário: {@code tipoMostrado} é o tipo que a revisão mostrou, devolvido só para o
 * servidor conferir que a situação não mudou (RF12, passo 6.1); o tipo gravado é sempre o
 * calculado (RN01). {@code chaveRequisicao} nasce na revisão (RN08).
 */
public class ManualForm {

	/** "2026-10-20T07:15", no fuso da escola (campo {@code datetime-local}). */
	@NotBlank(message = "Informe a data e a hora reais da movimentação.")
	private String ocorridoEm;

	/** RN07, item 2; RF12, exceções. */
	@NotBlank(message = "Informe o motivo do registro manual.")
	@Size(max = 200, message = "O motivo pode ter até 200 caracteres.")
	private String motivo;

	private String tipoMostrado;

	private String chaveRequisicao;

	public String getOcorridoEm() {
		return ocorridoEm;
	}

	public void setOcorridoEm(String ocorridoEm) {
		this.ocorridoEm = ocorridoEm;
	}

	public String getMotivo() {
		return motivo;
	}

	public void setMotivo(String motivo) {
		this.motivo = motivo;
	}

	public String getTipoMostrado() {
		return tipoMostrado;
	}

	public void setTipoMostrado(String tipoMostrado) {
		this.tipoMostrado = tipoMostrado;
	}

	public String getChaveRequisicao() {
		return chaveRequisicao;
	}

	public void setChaveRequisicao(String chaveRequisicao) {
		this.chaveRequisicao = chaveRequisicao;
	}

}
