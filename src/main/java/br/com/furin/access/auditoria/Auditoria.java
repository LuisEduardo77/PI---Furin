package br.com.furin.access.auditoria;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Log de auditoria separado (A10 §8.15; RNF05; arquitetura §6).
 * <p>
 * Cada evento vira uma linha no logger {@value #LOGGER}, que tem arquivo próprio
 * (logback-spring.xml), neste formato:
 * <pre>evento=ACESSO_NEGADO quando=2026-11-01T17:00:00.123Z usuario=a*** rota=/alunos</pre>
 * O horário é UTC (RN10). O usuário sai sempre mascarado. Quebras de linha nos
 * valores viram "_", para que um valor não forje outra linha do log.
 */
@Component
public class Auditoria {

	/** Nome do logger de auditoria, separado dos logs técnicos. */
	public static final String LOGGER = "AUDITORIA";

	static final String ANONIMO = "anonimo";

	private static final Logger log = LoggerFactory.getLogger(LOGGER);

	private final Clock relogio;

	public Auditoria(Clock relogio) {
		this.relogio = relogio;
	}

	/** Registra o evento em nome do funcionário autenticado (ou "anonimo"). */
	public void registrar(EventoAuditoria evento, DetalhesAuditoria detalhes) {
		registrar(evento, usuarioAtual(), detalhes);
	}

	/**
	 * Registra o evento em nome de um login informado, para quando ainda não há
	 * ninguém autenticado (por exemplo, {@link EventoAuditoria#LOGIN_FALHA}). O login sai
	 * mascarado.
	 */
	public void registrarComo(EventoAuditoria evento, String login, DetalhesAuditoria detalhes) {
		registrar(evento, Mascaramento.login(login), detalhes);
	}

	private void registrar(EventoAuditoria evento, String usuarioMascarado, DetalhesAuditoria detalhes) {
		if (!log.isInfoEnabled()) {
			return;
		}
		StringBuilder linha = new StringBuilder()
				.append("evento=").append(evento.name())
				.append(" quando=").append(Instant.now(relogio))
				.append(" usuario=").append(limpar(usuarioMascarado));
		for (Map.Entry<String, String> detalhe : detalhes.valores().entrySet()) {
			linha.append(' ').append(detalhe.getKey()).append('=').append(limpar(detalhe.getValue()));
		}
		log.info("{}", linha);
	}

	private static String usuarioAtual() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao == null || !autenticacao.isAuthenticated()
				|| autenticacao instanceof AnonymousAuthenticationToken) {
			return ANONIMO;
		}
		return Mascaramento.login(autenticacao.getName());
	}

	/** Sem quebra de linha nem tabulação; valor com espaço vai entre aspas. */
	static String limpar(String valor) {
		StringBuilder limpo = new StringBuilder(valor.length());
		for (char c : valor.toCharArray()) {
			limpo.append(Character.isISOControl(c) ? '_' : c);
		}
		String resultado = limpo.toString();
		return resultado.indexOf(' ') >= 0 ? '"' + resultado.replace('"', '\'') + '"' : resultado;
	}

}
