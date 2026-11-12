package br.com.furin.access.conta;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ResponseStatus;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.autenticacao.ContasDosFuncionarios;
import br.com.furin.access.autenticacao.Usuario;
import br.com.furin.access.autenticacao.UsuarioRepository;
import br.com.furin.access.config.FurinProperties;

/**
 * Contas da equipe (T-43; RF26; RN14; DEC-39): qualquer funcionário cria, gera nova senha
 * temporária, desativa e reativa contas {@code FUNCIONARIO}.
 * <p>
 * A senha temporária só existe em texto puro no {@link SenhaGerada} devolvido ao
 * controller, que a mostra uma vez. No banco fica só o hash (RNF03), e no log nunca entra:
 * os eventos levam o autor (o funcionário da sessão, mascarado pela {@link Auditoria}) e o
 * ID da conta. Toda ação sobre uma conta existente trava a linha ({@code FOR UPDATE}).
 */
@Service
public class ContasDaEquipe {

	private final UsuarioRepository usuarios;

	private final PasswordEncoder encoder;

	private final GeradorDeSenhaTemporaria gerador;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final Duration validadeTemporaria;

	public ContasDaEquipe(UsuarioRepository usuarios, PasswordEncoder encoder, GeradorDeSenhaTemporaria gerador,
			Auditoria auditoria, Clock relogio, FurinProperties propriedades) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.gerador = gerador;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.validadeTemporaria = propriedades.senha().validadeTemporaria();
	}

	/** O que o controller mostra uma vez: o nome da pessoa e a senha temporária. */
	public record SenhaGerada(long idConta, String nome, String senha, Motivo motivo) {

		/** Por que a senha foi gerada (muda o texto da tela). */
		public enum Motivo {
			CONTA_CRIADA, NOVA_SENHA, CONTA_REATIVADA
		}

		@Override
		public String toString() {
			// Nunca a senha, nem num log de depuração
			return "SenhaGerada[idConta=" + idConta + ", motivo=" + motivo + "]";
		}
	}

	/** O login informado já é de outra conta. */
	public static class LoginEmUso extends RuntimeException {

		private static final long serialVersionUID = 1L;

		LoginEmUso() {
			super("Login já em uso");
		}
	}

	/** RF26: ninguém desativa a própria conta (DEC-39). */
	public static class PropriaConta extends RuntimeException {

		private static final long serialVersionUID = 1L;

		PropriaConta() {
			super("Tentativa de desativar a própria conta");
		}
	}

	/** RF26, exceções: ID de conta inexistente. Vira 404 (error/404.html). */
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public static class ContaNaoEncontrada extends RuntimeException {

		private static final long serialVersionUID = 1L;

		ContaNaoEncontrada() {
			super("Conta não encontrada");
		}
	}

	@Transactional(readOnly = true)
	public List<Usuario> todas() {
		return usuarios.findAllByOrderByNomeAscLoginAsc();
	}

	/** A conta pedida, ou {@link ContaNaoEncontrada} (com o evento no log). */
	@Transactional(readOnly = true)
	public Usuario conta(long idConta) {
		return usuarios.findById(idConta).orElseThrow(() -> naoEncontrada(idConta));
	}

	/**
	 * RF26, "Criar": perfil sempre {@code FUNCIONARIO} (o parâmetro nem existe), login
	 * normalizado e único, senha temporária com prazo de {@code furin.senha.validade-temporaria}.
	 */
	@Transactional
	public SenhaGerada criar(String nome, String login) {
		String normalizado = ContasDosFuncionarios.normalizarLogin(login);
		if (usuarios.existsByLogin(normalizado)) {
			throw new LoginEmUso();
		}
		String senha = gerador.gerar();
		Instant agora = relogio.instant();
		Usuario conta = usuarios.saveAndFlush(Usuario.novaConta(nome.strip(), normalizado, encoder.encode(senha),
				agora.plus(validadeTemporaria), agora));
		auditoria.registrar(EventoAuditoria.CONTA_CRIADA, detalhes().id("idConta", conta.getId()));
		return new SenhaGerada(conta.getId(), conta.getNome(), senha, SenhaGerada.Motivo.CONTA_CRIADA);
	}

	/** RF26: nova senha temporária. A anterior deixa de valer (RN14). */
	@Transactional
	public SenhaGerada gerarNovaSenha(long idConta) {
		Usuario conta = travar(idConta);
		String senha = novaSenhaTemporaria(conta);
		return new SenhaGerada(idConta, conta.getNome(), senha, SenhaGerada.Motivo.NOVA_SENHA);
	}

	/**
	 * RF26: desativa. Uma sessão aberta da conta é encerrada no próximo pedido
	 * (autenticacao/ContaDesativada). Já inativa, não muda nada.
	 *
	 * @param loginDoAutor o funcionário da sessão
	 */
	@Transactional
	public void desativar(long idConta, String loginDoAutor) {
		Usuario conta = travar(idConta);
		if (propria(conta, loginDoAutor)) {
			throw new PropriaConta();
		}
		if (conta.isAtivo()) {
			conta.desativar();
			usuarios.saveAndFlush(conta);
			auditoria.registrar(EventoAuditoria.CONTA_DESATIVADA, detalhes().id("idConta", idConta));
		}
	}

	/**
	 * RF26: reativa com uma nova senha temporária [PROP: detalhe do RF26]. Já ativa, não
	 * muda nada e devolve vazio.
	 */
	@Transactional
	public Optional<SenhaGerada> reativar(long idConta) {
		Usuario conta = travar(idConta);
		if (conta.isAtivo()) {
			return Optional.empty();
		}
		conta.reativar();
		auditoria.registrar(EventoAuditoria.CONTA_REATIVADA, detalhes().id("idConta", idConta));
		String senha = novaSenhaTemporaria(conta);
		return Optional.of(new SenhaGerada(idConta, conta.getNome(), senha, SenhaGerada.Motivo.CONTA_REATIVADA));
	}

	/** A conta é a do funcionário da sessão? */
	public static boolean propria(Usuario conta, String loginDoAutor) {
		return conta.getLogin().equals(ContasDosFuncionarios.normalizarLogin(loginDoAutor));
	}

	private String novaSenhaTemporaria(Usuario conta) {
		String senha = gerador.gerar();
		conta.definirSenhaTemporaria(encoder.encode(senha), relogio.instant().plus(validadeTemporaria));
		usuarios.saveAndFlush(conta);
		auditoria.registrar(EventoAuditoria.SENHA_TEMPORARIA_GERADA, detalhes().id("idConta", conta.getId()));
		return senha;
	}

	private Usuario travar(long idConta) {
		return usuarios.travarPorId(idConta).orElseThrow(() -> naoEncontrada(idConta));
	}

	private ContaNaoEncontrada naoEncontrada(long idConta) {
		auditoria.registrar(EventoAuditoria.CONTA_NAO_ENCONTRADA, detalhes().id("idConta", idConta));
		return new ContaNaoEncontrada();
	}

}
