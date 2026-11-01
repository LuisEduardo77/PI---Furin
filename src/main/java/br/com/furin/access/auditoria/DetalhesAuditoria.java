package br.com.furin.access.auditoria;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detalhes de um evento de auditoria. Só aceita tipos seguros: IDs, textos técnicos
 * (rota, motivo), nomes de campos e dados pessoais que já saem mascarados.
 * <p>
 * Não existe método para senha, conteúdo de documento, foto ou termo: esses dados
 * nunca vão para o log (A10 §8.15; RNF03; RNF05).
 */
public final class DetalhesAuditoria {

	private final Map<String, String> valores = new LinkedHashMap<>();

	private DetalhesAuditoria() {
	}

	public static DetalhesAuditoria detalhes() {
		return new DetalhesAuditoria();
	}

	/** ID técnico, por exemplo {@code idAluno}. */
	public DetalhesAuditoria id(String nome, long valor) {
		return colocar(nome, Long.toString(valor));
	}

	/** Texto técnico sem dado pessoal, por exemplo a rota ou o motivo de uma recusa. */
	public DetalhesAuditoria texto(String nome, String valor) {
		return colocar(nome, valor == null ? "" : valor);
	}

	/** Nomes de campos alterados, sem os valores (RF08). */
	public DetalhesAuditoria campos(Collection<String> nomes) {
		return colocar("campos", String.join(",", nomes));
	}

	/** E-mail, sempre mascarado. */
	public DetalhesAuditoria email(String nome, String email) {
		return colocar(nome, Mascaramento.email(email));
	}

	/** Código da carteirinha, só com os 4 primeiros caracteres. */
	public DetalhesAuditoria codigoCarteirinha(String codigo) {
		return colocar("codigo", Mascaramento.codigoCarteirinha(codigo));
	}

	Map<String, String> valores() {
		return valores;
	}

	private DetalhesAuditoria colocar(String nome, String valor) {
		if (!nomeValido(nome)) {
			throw new IllegalArgumentException("Nome de detalhe inválido no log de auditoria: " + nome);
		}
		valores.put(nome, valor);
		return this;
	}

	/** Só letras e dígitos, começando por letra: o nome nunca quebra o formato da linha. */
	private static boolean nomeValido(String nome) {
		if (nome == null || nome.isEmpty() || !Character.isLetter(nome.charAt(0))) {
			return false;
		}
		return nome.chars().allMatch(Character::isLetterOrDigit);
	}

}
