package br.com.furin.access.autenticacao;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Leitura e troca da senha do funcionário (T-15; RF01, fluxos A1 e A3; RNF03). A senha
 * só é comparada e gravada pelo {@code PasswordEncoder} delegante: nunca em texto puro,
 * nunca em log.
 */
@Service
public class SenhasDosFuncionarios {

	private final UsuarioRepository usuarios;

	private final PasswordEncoder encoder;

	public SenhasDosFuncionarios(UsuarioRepository usuarios, PasswordEncoder encoder) {
		this.usuarios = usuarios;
		this.encoder = encoder;
	}

	/** A conta do funcionário autenticado. */
	@Transactional(readOnly = true)
	public Usuario conta(String login) {
		return buscar(login);
	}

	/** A senha informada é a senha atual da conta (a temporária, no fluxo A1)? */
	public boolean ehASenhaAtual(Usuario conta, String senha) {
		return encoder.matches(senha, conta.getSenhaHash());
	}

	/**
	 * Grava a nova senha como hash e encerra a senha temporária (RF01, fluxo A1, passo 3;
	 * fluxo A3, passo 4; CA-RF01-06).
	 */
	@Transactional
	public void trocar(String login, String novaSenha) {
		// Sem chamar conta(): pelo próprio objeto, o @Transactional dela seria ignorado
		Usuario conta = buscar(login);
		conta.trocarSenha(encoder.encode(novaSenha));
		// Grava já: a troca vale antes da próxima consulta, mesmo dentro de uma transação maior
		usuarios.saveAndFlush(conta);
	}

	private Usuario buscar(String login) {
		return usuarios.findByLogin(ContasDosFuncionarios.normalizarLogin(login))
				.orElseThrow(() -> new IllegalStateException("Conta do funcionário autenticado não encontrada"));
	}

}
