package br.com.furin.access.aluno;

/**
 * Formulário "Editar aluno" (RF08): os dados comuns de {@link DadosDoAlunoForm}, a
 * substituição do contato principal (fluxo A1) e o aviso por e-mail (fluxo A5). A foto é
 * opcional: sem uma nova, a atual continua (CA-RF08-04).
 */
public class EdicaoForm extends DadosDoAlunoForm {

	private static final long serialVersionUID = 1L;

	/** RF08, fluxo A1: a pessoa do contato principal só muda por um contato novo. */
	private boolean substituirContato;

	/** RF08, fluxo A5; DEC-38: "Enviar avisos de entrada e saída por e-mail ao contato principal". */
	private boolean recebeAviso;

	/** Uma cópia sem a foto, para guardar na sessão até a confirmação. */
	EdicaoForm semFoto() {
		EdicaoForm copia = new EdicaoForm();
		copia.setNome(getNome());
		copia.setMatricula(getMatricula());
		copia.setIdTurma(getIdTurma());
		copia.setContatoNome(getContatoNome());
		copia.setContatoTelefone(getContatoTelefone());
		copia.setContatoEmail(getContatoEmail());
		copia.substituirContato = substituirContato;
		copia.recebeAviso = recebeAviso;
		return copia;
	}

	public boolean isSubstituirContato() {
		return substituirContato;
	}

	public void setSubstituirContato(boolean substituirContato) {
		this.substituirContato = substituirContato;
	}

	public boolean isRecebeAviso() {
		return recebeAviso;
	}

	public void setRecebeAviso(boolean recebeAviso) {
		this.recebeAviso = recebeAviso;
	}

}
