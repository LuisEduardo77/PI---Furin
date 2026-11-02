package br.com.furin.access.portaria;

/**
 * Por que uma leitura foi recusada (RN09, RN06). Nenhuma recusa grava movimentação, e
 * todas vão para o log como {@code LEITURA_RECUSADA}. Os textos para o funcionário e
 * para o aluno são da T-31 (RF23); aqui fica só o código técnico.
 */
public enum MotivoDaRecusa {
	/** Código fora do formato ou que não existe (RN09). */
	CODIGO_NAO_RECONHECIDO,
	/** Carteirinha revogada, por exemplo depois de uma segunda via (RN09; RN15). */
	CARTEIRINHA_REVOGADA,
	/** Carteirinha com {@code expira_em} no passado (RN09). */
	CARTEIRINHA_EXPIRADA,
	/** Aluno inativo (RN09). */
	ALUNO_INATIVO,
	/** Aluno sem vínculo de turma vigente (RN09). */
	SEM_TURMA_VIGENTE,
	/** Entrada de dia anterior sem saída (RN01, linha 3; RN06). */
	SAIDA_PENDENTE,
	/**
	 * O aluno já tem uma liberação aguardando giro. A T-27 completa a RN04 ("Aguardando
	 * passagem") e a RN05 (outro aluno) [PROP].
	 */
	LIBERACAO_PENDENTE
}
