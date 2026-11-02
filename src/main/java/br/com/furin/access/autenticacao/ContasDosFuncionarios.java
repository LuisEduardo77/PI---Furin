package br.com.furin.access.autenticacao;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
 * {@code ativo = 0} fica desabilitada e não entra, com a mesma mensagem de credencial
 * inválida (RF02; LoginComFalhaAuditado).
 * <p>
 * T-15: uma senha temporária vencida (RN14; CA-RF01-05) vira "credencial expirada". O
 * Spring Security só confere isso <b>depois</b> da senha, então a orientação de procurar
 * a escola só aparece para quem acertou a senha e não revela quais contas existem.
 */
@Service
public class ContasDosFuncionarios implements UserDetailsService {

	private final UsuarioRepository usuarios;

	private final Clock relogio;

	public ContasDosFuncionarios(UsuarioRepository usuarios, Clock relogio) {
		this.usuarios = usuarios;
		this.relogio = relogio;
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
		return new ContaAutenticada(usuario.getLogin(), usuario.getSenhaHash(), usuario.isAtivo(),
				!senhaTemporariaVencida(usuario),
				List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil())),
				usuario.isTrocaSenhaObrigatoria());
	}

	/**
	 * A conta do funcionário autenticado foi desativada (T-17; RF03, exceções)? Conta que não
	 * existe não conta como desativada: no sistema nada é apagado fisicamente, então isso só
	 * acontece com os usuários de teste ({@code @WithMockUser}).
	 */
	@Transactional(readOnly = true)
	public boolean contaDesativada(String login) {
		return usuarios.existsByLoginAndAtivoFalse(normalizarLogin(login));
	}

	/**
	 * A senha temporária passou do prazo gravado em {@code senha_temporaria_expira_em}
	 * (emissão + {@code furin.senha.validade-temporaria}; RN14)? Os horários são UTC (RN10).
	 */
	boolean senhaTemporariaVencida(Usuario usuario) {
		Instant expiraEm = usuario.getSenhaTemporariaExpiraEm();
		return usuario.isTrocaSenhaObrigatoria() && expiraEm != null && !relogio.instant().isBefore(expiraEm);
	}

}
