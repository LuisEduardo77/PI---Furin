package br.com.furin.access.autenticacao;

import java.util.Locale;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carrega a conta do banco para o login do Spring Security (T-12; RF01, RF03).
 * <p>
 * O login digitado é normalizado antes da busca (RF01, fluxo principal, passo 3;
 * CA-RF01-02). O perfil vem do banco, nunca da tela (DEC-37). Uma conta com
 * {@code ativo = 0} fica desabilitada e não entra; a mensagem igual à de credencial
 * inválida e o log da tentativa são da T-13 (RF02).
 */
@Service
public class ContasDosFuncionarios implements UserDetailsService {

	private final UsuarioRepository usuarios;

	public ContasDosFuncionarios(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	/** Tira os espaços das pontas e passa para minúsculas: " ATE.Demo " vira "ate.demo". */
	public static String normalizarLogin(String login) {
		return login == null ? "" : login.strip().toLowerCase(Locale.ROOT);
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String login) {
		Usuario usuario = usuarios.findByLogin(normalizarLogin(login))
				// Sem o login na mensagem: ela não pode revelar quais contas existem
				.orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada"));
		return User.withUsername(usuario.getLogin())
				.password(usuario.getSenhaHash())
				.roles(usuario.getPerfil())
				.disabled(!usuario.isAtivo())
				.build();
	}

}
