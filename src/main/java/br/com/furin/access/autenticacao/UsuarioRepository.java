package br.com.furin.access.autenticacao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	/** Contas da equipe, pelo nome (T-43; RF26, "Listar"). */
	List<Usuario> findAllByOrderByNomeAscLoginAsc();

	/** O login precisa chegar já normalizado. */
	boolean existsByLogin(String login);

	/** A conta com {@code SELECT … FOR UPDATE}: duas ações na mesma conta não se cruzam (T-43). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT u FROM Usuario u WHERE u.id = :id")
	Optional<Usuario> travarPorId(Long id);

	/** O login precisa chegar já normalizado ({@link ContasDosFuncionarios#normalizarLogin}). */
	Optional<Usuario> findByLogin(String login);

	/** Existe a conta com esse login e ela está desativada? (T-17; RF03) */
	boolean existsByLoginAndAtivoFalse(String login);

}
