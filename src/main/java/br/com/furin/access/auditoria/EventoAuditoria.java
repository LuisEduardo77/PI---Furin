package br.com.furin.access.auditoria;

/**
 * Eventos do log de auditoria (A10 §8.15; RNF05), com os nomes definidos nos RF.
 * Um evento novo entra aqui, com a fonte.
 */
public enum EventoAuditoria {

	/** RF01: login com sucesso. [PROP: nome] */
	LOGIN_SUCESSO,
	/** RF02. */
	LOGIN_FALHA,
	/** RF02. */
	LOGIN_BLOQUEADO,
	/** RF01, fluxo A3. */
	SENHA_ALTERADA,
	/** RF03: rota negada (403). */
	ACESSO_NEGADO,

	/** RF26. */
	CONTA_CRIADA,
	/** RF26. */
	SENHA_TEMPORARIA_GERADA,
	/** RF26. */
	CONTA_DESATIVADA,
	/** RF26. */
	CONTA_REATIVADA,

	/** RF04. */
	ALUNO_CADASTRADO,
	/** RF08: só os nomes dos campos alterados, sem os valores. */
	ALUNO_EDITADO,
	/** RF08; DEC-38. */
	PREFERENCIA_AVISO_ALTERADA,
	/** RF05; DEC-34: só o ID do consentimento, nunca o conteúdo do termo. */
	CONSENTIMENTO_REVOGADO,
	/** RF07. */
	CARTEIRINHA_SEGUNDA_VIA,

	/** RF12. */
	REGISTRO_MANUAL,
	/** RF23: só os 4 primeiros caracteres do código lido (DEC-28, A12). */
	LEITURA_RECUSADA,
	/** RF10; DEC-28, A9: foto ou termo que não pôde ser entregue. Só a chave. [PROP: nome] */
	ARQUIVO_INDISPONIVEL,
	/** RF20. */
	RELATORIO_IMPRESSO,

	/** RF21: envio simulado (DEC-32). */
	NOTIFICACAO_SIMULADA,
	/** RF21: só numa evolução com envio real. */
	NOTIFICACAO_ENVIADA,
	/** RF21: só numa evolução com envio real. */
	NOTIFICACAO_FALHA

}
