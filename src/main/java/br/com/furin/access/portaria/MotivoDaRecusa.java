package br.com.furin.access.portaria;

/**
 * Por que uma leitura foi recusada (RN09, RN06). Nenhuma recusa grava movimentação, e
 * todas vão para o log como {@code LEITURA_RECUSADA}. Os textos ficam na tela
 * (static/js/portaria.js, T-31): o motivo para o funcionário (RF23, passo 1) e sempre a
 * mesma mensagem para o aluno (RF23, passo 2).
 */
public enum MotivoDaRecusa {
	/**
	 * RF23, passo 1: conteúdo fora do formato (diferente de 32 caracteres hexadecimais).
	 * Recusado antes de qualquer consulta à carteirinha ou ao aluno (CA-RF23-04).
	 */
	LEITURA_NAO_RECONHECIDA,
	/** Código no formato, mas que não existe (RN09). */
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
	/** Outro aluno aguarda o giro: uma liberação por vez (RN05; DEC-46). */
	OUTRA_LIBERACAO_PENDENTE
}
