package br.com.furin.access.aluno;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Formulário "Novo aluno" (RF04): os dados comuns de {@link DadosDoAlunoForm} e o termo de
 * consentimento (RF05). A data e o arquivo do termo são conferidos no controller, porque
 * dependem do relógio e de {@code furin.arquivos.*}. Não existe campo de conferente nem de
 * instante da conferência: os dois vêm do servidor (CA-RF05-04).
 */
public class AlunoForm extends DadosDoAlunoForm {

	private static final long serialVersionUID = 1L;

	/** RF05, passo 1.1: no cadastro novo, o único contato é o principal [PROP]. */
	private String termoSignatario;

	@NotBlank(message = "Informe a versão do termo.")
	@Size(max = 30, message = "A versão do termo pode ter até 30 caracteres.")
	private String termoVersao;

	private String termoAssinadoEm;

	@Size(max = 255, message = "O protocolo pode ter até 255 caracteres.")
	private String termoProtocolo;

	private transient MultipartFile termoArquivo;

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

}
