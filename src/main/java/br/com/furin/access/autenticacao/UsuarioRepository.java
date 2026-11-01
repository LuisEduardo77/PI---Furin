package br.com.furin.access.autenticacao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** O login precisa chegar já normalizado ({@link ContasDosFuncionarios#normalizarLogin}). */
	Optional<Usuario> findByLogin(String login);

}
