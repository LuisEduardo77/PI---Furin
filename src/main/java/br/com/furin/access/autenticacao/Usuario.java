package br.com.furin.access.autenticacao;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Conta de um funcionário (tabela {@code usuario}, arquitetura §5.5). Só leitura por
 * enquanto: a T-12 usa a conta para o login. A criação e a troca de senha vêm na T-15
 * e na T-43.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id_usuario")
	private Long id;

	private String nome;

	/** Normalizado: sem espaços nas pontas e em minúsculas (arquitetura §5.5). */
	private String login;

	@Column(name = "senha_hash")
	private String senhaHash;

	/** Sempre FUNCIONARIO (DEC-37), gravado pelo servidor. */
	private String perfil;

	private boolean ativo;

	@Column(name = "troca_senha_obrigatoria")
	private boolean trocaSenhaObrigatoria;

	/** UTC (RN10). */
	@Column(name = "senha_temporaria_expira_em")
	private LocalDateTime senhaTemporariaExpiraEm;

	/** UTC (RN10). */
	@Column(name = "criado_em")
	private LocalDateTime criadoEm;

	protected Usuario() {
		// JPA
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getLogin() {
		return login;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public String getPerfil() {
		return perfil;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public boolean isTrocaSenhaObrigatoria() {
		return trocaSenhaObrigatoria;
	}

	public LocalDateTime getSenhaTemporariaExpiraEm() {
		return senhaTemporariaExpiraEm;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

}
