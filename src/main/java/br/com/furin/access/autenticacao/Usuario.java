package br.com.furin.access.autenticacao;

import java.time.Instant;
import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Conta de um funcionário (tabela {@code usuario}, arquitetura §5.5). A T-12 usa a conta
 * para o login, e a T-15 troca a senha ({@link #trocarSenha}). A criação vem na T-43.
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

	/**
	 * Gravado em UTC (RN10). É {@code Instant}, e não {@code LocalDateTime}, para ser
	 * comparado com o relógio sem depender do fuso da JVM: com
	 * {@code hibernate.jdbc.time_zone: UTC}, um {@code LocalDateTime} lido do banco vem no
	 * fuso da JVM (T-15).
	 */
	@Column(name = "senha_temporaria_expira_em")
	@JdbcTypeCode(SqlTypes.TIMESTAMP)
	private Instant senhaTemporariaExpiraEm;

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

	public Instant getSenhaTemporariaExpiraEm() {
		return senhaTemporariaExpiraEm;
	}

	public LocalDateTime getCriadoEm() {
		return criadoEm;
	}

	/**
	 * Grava a nova senha, já como hash (RNF03), e encerra a senha temporária (RF01, fluxo
	 * A1, passo 3; CA-RF01-06): sem troca pendente e sem prazo.
	 */
	void trocarSenha(String novoHash) {
		this.senhaHash = novoHash;
		this.trocaSenhaObrigatoria = false;
		this.senhaTemporariaExpiraEm = null;
	}

}
