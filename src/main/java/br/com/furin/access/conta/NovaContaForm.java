package br.com.furin.access.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Nova conta" (T-43; RF26, "Criar", passo 1). Só nome e login: o perfil não é campo,
 * então um {@code perfil} enviado pela interface nem chega ao servidor (CA-RF26-03).
 * Os tamanhos seguem a tabela {@code usuario} (VARCHAR(150) e VARCHAR(80)). Mensagens
 * [PROP: texto do RF26].
 */
public class NovaContaForm {

	@NotBlank(message = "Informe o nome.")
	@Size(max = 150, message = "O nome pode ter até 150 caracteres.")
	private String nome;

	@NotBlank(message = "Informe o login.")
	@Size(max = 80, message = "O login pode ter até 80 caracteres.")
	private String login;

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getLogin() {
		return login;
	}

	public void setLogin(String login) {
		// Sem espaços nas pontas já na conferência do tamanho; as minúsculas vêm no serviço
		this.login = login == null ? null : login.strip();
	}

}
