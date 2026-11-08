package br.com.furin.access.aluno;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulário "Editar aluno" (RF08), com as mesmas mensagens do RF04. A foto é opcional: sem
 * uma nova, a atual continua (CA-RF08-04). A turma, o telefone e a foto são conferidos em
 * {@link ConferenciasDoAluno}.
 */
public class EdicaoForm {

	@NotBlank(message = "Informe o nome do aluno.")
	@Size(max = 150, message = "O nome do aluno pode ter até 150 caracteres.")
	private String nome;

	@NotBlank(message = "Informe a matrícula do aluno.")
	@Size(max = 30, message = "A matrícula pode ter até 30 caracteres.")
	private String matricula;

	private String idTurma;

	private MultipartFile foto;

	/** RF08, fluxo A1: a pessoa do contato principal só muda por um contato novo. */
	private boolean substituirContato;

	@NotBlank(message = "Informe o nome do responsável.")
	@Size(max = 150, message = "O nome do responsável pode ter até 150 caracteres.")
	private String contatoNome;

	@NotBlank(message = "Informe o telefone com DDD. Exemplo: (11) 90000-0000.")
	private String contatoTelefone;

	@NotBlank(message = AlunoForm.EMAIL_INVALIDO)
	@Email(message = AlunoForm.EMAIL_INVALIDO)
	@Size(max = 254, message = AlunoForm.EMAIL_INVALIDO)
	private String contatoEmail;

	/** RF08, fluxo A5; DEC-38: "Enviar avisos de entrada e saída por e-mail ao contato principal". */
	private boolean recebeAviso;

	/** Uma cópia sem a foto, para guardar na sessão até a confirmação. */
	EdicaoForm semFoto() {
		EdicaoForm copia = new EdicaoForm();
		copia.nome = nome;
		copia.matricula = matricula;
		copia.idTurma = idTurma;
		copia.substituirContato = substituirContato;
		copia.contatoNome = contatoNome;
		copia.contatoTelefone = contatoTelefone;
		copia.contatoEmail = contatoEmail;
		copia.recebeAviso = recebeAviso;
		return copia;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getMatricula() {
		return matricula;
	}

	public void setMatricula(String matricula) {
		this.matricula = matricula;
	}

	public String getIdTurma() {
		return idTurma;
	}

	public void setIdTurma(String idTurma) {
		this.idTurma = idTurma;
	}

	public MultipartFile getFoto() {
		return foto;
	}

	public void setFoto(MultipartFile foto) {
		this.foto = foto;
	}

	public boolean isSubstituirContato() {
		return substituirContato;
	}

	public void setSubstituirContato(boolean substituirContato) {
		this.substituirContato = substituirContato;
	}

	public String getContatoNome() {
		return contatoNome;
	}

	public void setContatoNome(String contatoNome) {
		this.contatoNome = contatoNome;
	}

	public String getContatoTelefone() {
		return contatoTelefone;
	}

	public void setContatoTelefone(String contatoTelefone) {
		this.contatoTelefone = contatoTelefone;
	}

	public String getContatoEmail() {
		return contatoEmail;
	}

	public void setContatoEmail(String contatoEmail) {
		this.contatoEmail = contatoEmail;
	}

	public boolean isRecebeAviso() {
		return recebeAviso;
	}

	public void setRecebeAviso(boolean recebeAviso) {
		this.recebeAviso = recebeAviso;
	}

}
