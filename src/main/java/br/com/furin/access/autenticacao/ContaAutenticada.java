package br.com.furin.access.autenticacao;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

/**
 * Funcionário autenticado, com a informação de que ainda precisa trocar a senha
 * temporária (RF01, fluxo principal, passo 5; T-15). LoginComSucessoAuditado usa isso para
 * levar à tela "Definir nova senha" em vez da portaria.
 */
public class ContaAutenticada extends User {

	private static final long serialVersionUID = 1L;

	private final boolean trocaSenhaObrigatoria;

	ContaAutenticada(String login, String senhaHash, boolean ativa, boolean senhaTemporariaValida,
			Collection<? extends GrantedAuthority> perfis, boolean trocaSenhaObrigatoria) {
		super(login, senhaHash, ativa, true, senhaTemporariaValida, true, perfis);
		this.trocaSenhaObrigatoria = trocaSenhaObrigatoria;
	}

	public boolean isTrocaSenhaObrigatoria() {
		return trocaSenhaObrigatoria;
	}

	@Override
	public boolean equals(Object outro) {
		// Mesma identidade de User: o login
		return super.equals(outro);
	}

	@Override
	public int hashCode() {
		return super.hashCode();
	}

}
