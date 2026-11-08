package br.com.furin.access.portaria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;

/**
 * Cancelamento pelo funcionário e consulta da situação (T-29; RF09, fluxos A1 e A2; RN02,
 * itens 4 e 5; DEC-28, A2; DEC-37). As duas operações começam pela expiração, para nunca
 * responderem "pendente" sobre uma liberação que já passou do prazo.
 */
@Service
public class Liberacoes {

	/** Por que o cancelamento foi recusado. */
	public enum MotivoDoCancelamentoRecusado {
		/** Não existe liberação com esse ID. */
		LIBERACAO_NAO_ENCONTRADA,
		/** RN02, item 5: uma movimentação já confirmada nunca é editada. */
		LIBERACAO_CONFIRMADA
	}

	/** Cancelamento recusado. Nada foi alterado. */
	public static class CancelamentoRecusado extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final MotivoDoCancelamentoRecusado motivo;

		CancelamentoRecusado(MotivoDoCancelamentoRecusado motivo) {
			super(motivo.name(), null, false, false);
			this.motivo = motivo;
		}

		public MotivoDoCancelamentoRecusado getMotivo() {
			return motivo;
		}
	}

	private final RegistrosDaPortaria registros;

	private final Expiracoes expiracoes;

	private final Auditoria auditoria;

	public Liberacoes(RegistrosDaPortaria registros, Expiracoes expiracoes, Auditoria auditoria) {
		this.registros = registros;
		this.expiracoes = expiracoes;
		this.auditoria = auditoria;
	}

	/**
	 * RN02, item 5: a liberação pendente vira {@code CANCELADA} com {@code FUNCIONARIO}, e o
	 * evento vai para o log. Cancelar de novo uma liberação já cancelada (ou expirada)
	 * devolve a situação dela, sem mudar nada; a confirmada é recusada.
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = CancelamentoRecusado.class)
	public SituacaoDaLiberacao cancelar(long idLiberacao) {
		expiracoes.expirarVencidas();
		long idAluno = registros.alunoDaLiberacao(idLiberacao)
				.orElseThrow(() -> new CancelamentoRecusado(MotivoDoCancelamentoRecusado.LIBERACAO_NAO_ENCONTRADA));
		registros.bloquearAluno(idAluno);

		if (registros.cancelarPeloFuncionario(idLiberacao, expiracoes.agora())) {
			auditoria.registrar(EventoAuditoria.LIBERACAO_CANCELADA,
					detalhes().id("liberacao", idLiberacao).texto("motivo", RegistrosDaPortaria.FUNCIONARIO));
		}
		SituacaoDaLiberacao situacao = registros.situacao(idLiberacao).orElseThrow();
		if (RegistrosDaPortaria.CONFIRMADA.equals(situacao.situacao())) {
			throw new CancelamentoRecusado(MotivoDoCancelamentoRecusado.LIBERACAO_CONFIRMADA);
		}
		return situacao;
	}

	/** A situação atual, depois de expirar as vencidas. Vazia se a liberação não existe. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<SituacaoDaLiberacao> situacao(long idLiberacao) {
		expiracoes.expirarVencidas();
		return registros.situacao(idLiberacao);
	}

}
