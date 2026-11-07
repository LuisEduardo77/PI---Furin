package br.com.furin.access.aluno;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulário "Novo aluno" (RF04) com o termo de consentimento (RF05). Só os dados
 * permitidos pelo RNF05: sem CPF, endereço, nascimento nem biometria (CA-RF04-07). As
 * mensagens vêm do RF04 e do RF05; a do nome do responsável e as de tamanho são [PROP].
 * A turma, a foto, a data e o arquivo do termo são conferidos no controller, porque
 * dependem do banco, do relógio e de {@code furin.arquivos.*}. Não existe campo de
 * conferente nem de instante da conferência: os dois vêm do servidor (CA-RF05-04).
 */
public class AlunoForm {

	static final String EMAIL_INVALIDO = "Informe um e-mail válido. Exemplo: nome@example.com.";

	@NotBlank(message = "Informe o nome do aluno.")
	@Size(max = 150, message = "O nome do aluno pode ter até 150 caracteres.")
	private String nome;

	@NotBlank(message = "Informe a matrícula do aluno.")
	@Size(max = 30, message = "A matrícula pode ter até 30 caracteres.")
	private String matricula;

	private String idTurma;

	private MultipartFile foto;

	@NotBlank(message = "Informe o nome do responsável.")
	@Size(max = 150, message = "O nome do responsável pode ter até 150 caracteres.")
	private String contatoNome;

	@NotBlank(message = "Informe o telefone com DDD. Exemplo: (11) 90000-0000.")
	private String contatoTelefone;

	@NotBlank(message = EMAIL_INVALIDO)
	@Email(message = EMAIL_INVALIDO)
	@Size(max = 254, message = EMAIL_INVALIDO)
	private String contatoEmail;

	// --- Termo de consentimento (RF05). A data e o arquivo são conferidos no controller ---

	/** RF05, passo 1.1: no cadastro novo, o único contato é o principal [PROP]. */
	private String termoSignatario;

	@NotBlank(message = "Informe a versão do termo.")
	@Size(max = 30, message = "A versão do termo pode ter até 30 caracteres.")
	private String termoVersao;

	private String termoAssinadoEm;

	@Size(max = 255, message = "O protocolo pode ter até 255 caracteres.")
	private String termoProtocolo;

	private MultipartFile termoArquivo;

	@AssertTrue(message = "Confirme que o termo de consentimento assinado foi conferido.")
	private boolean termoConferido;

	public String getTermoSignatario() {
		return termoSignatario;
	}

	public void setTermoSignatario(String termoSignatario) {
		this.termoSignatario = termoSignatario;
	}

	public String getTermoVersao() {
		return termoVersao;
	}

	public void setTermoVersao(String termoVersao) {
		this.termoVersao = termoVersao;
	}

	public String getTermoAssinadoEm() {
		return termoAssinadoEm;
	}

	public void setTermoAssinadoEm(String termoAssinadoEm) {
		this.termoAssinadoEm = termoAssinadoEm;
	}

	public String getTermoProtocolo() {
		return termoProtocolo;
	}

	public void setTermoProtocolo(String termoProtocolo) {
		this.termoProtocolo = termoProtocolo;
	}

	public MultipartFile getTermoArquivo() {
		return termoArquivo;
	}

	public void setTermoArquivo(MultipartFile termoArquivo) {
		this.termoArquivo = termoArquivo;
	}

	public boolean isTermoConferido() {
		return termoConferido;
	}

	public void setTermoConferido(boolean termoConferido) {
		this.termoConferido = termoConferido;
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
