package br.com.furin.access.aluno;

import java.io.Serializable;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Os campos que o cadastro (RF04) e a edição (RF08) têm em comum, com as mensagens do RF04.
 * Só os dados permitidos pelo RNF05: sem CPF, endereço, nascimento nem biometria. A turma,
 * o telefone e a foto são conferidos em {@link ConferenciasDoAluno}. A mensagem do nome do
 * responsável e as de tamanho são [PROP].
 * <p>
 * Serializável porque a edição guarda os valores revisados na sessão (T-23). A foto não vai
 * junto: o arquivo é guardado à parte.
 */
public abstract class DadosDoAlunoForm implements Serializable {

	private static final long serialVersionUID = 1L;

	static final String EMAIL_INVALIDO = "Informe um e-mail válido. Exemplo: nome@example.com.";

	@NotBlank(message = "Informe o nome do aluno.")
	@Size(max = 150, message = "O nome do aluno pode ter até 150 caracteres.")
	private String nome;

	@NotBlank(message = "Informe a matrícula do aluno.")
	@Size(max = 30, message = "A matrícula pode ter até 30 caracteres.")
	private String matricula;

	private String idTurma;

	private transient MultipartFile foto;

	@NotBlank(message = "Informe o nome do responsável.")
	@Size(max = 150, message = "O nome do responsável pode ter até 150 caracteres.")
	private String contatoNome;

	@NotBlank(message = "Informe o telefone com DDD. Exemplo: (11) 90000-0000.")
	private String contatoTelefone;

	@NotBlank(message = EMAIL_INVALIDO)
	@Email(message = EMAIL_INVALIDO)
	@Size(max = 254, message = EMAIL_INVALIDO)
	private String contatoEmail;

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

}
