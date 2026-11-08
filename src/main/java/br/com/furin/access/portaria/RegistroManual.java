package br.com.furin.access.portaria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.portaria.RegistrosDaPortaria.AlunoDoManual;
import br.com.furin.access.portaria.RegistrosDaPortaria.Manual;
import br.com.furin.access.portaria.RegistrosDaPortaria.Vinculo;
import br.com.furin.access.portaria.TipoAutomatico.Decisao;
import br.com.furin.access.portaria.TipoAutomatico.UltimaMovimentacao;

/**
 * Registro manual excepcional (T-32; RF12; RN07; DEC-03, DEC-04, DEC-20; DEC-28, B1 e B2).
 * <ul>
 * <li>O tipo é sempre do servidor (RN01): a saída pendente de dia anterior, que na leitura é
 * recusada, aqui vira uma {@code SAIDA}, para fechar a pendência (RN06);</li>
 * <li>o horário informado vai para {@code ocorrido_em}: não pode ser posterior ao lançamento
 * nem anterior ou igual à última movimentação confirmada (RN07, item 4);</li>
 * <li>a gravação é {@code MANUAL} e {@code CONFIRMADA}, sem carteirinha, numa transação com o
 * aluno bloqueado (RN08), e é recusada se houver uma liberação pendente para ele (RN07,
 * item 6). Um reenvio com a mesma {@code chaveRequisicao} devolve o registro original.</li>
 * </ul>
 * As duas operações começam pela expiração das liberações sem giro (T-29). A notificação
 * (RF12, passo 6.4) é da T-38.
 */
@Service
public class RegistroManual {

	/** Por que o registro manual não foi gravado. Nada foi alterado. */
	public enum Motivo {
		/** Aluno que não existe. */
		ALUNO_NAO_ENCONTRADO,
		/** Aluno inativo ou sem turma vigente (RF12, exceções). */
		SEM_REGISTRO_POSSIVEL,
		/** RN07, item 6. */
		LIBERACAO_PENDENTE,
		/** RF12, passo 6.1: o tipo mudou entre a tela e a confirmação. */
		SITUACAO_MUDOU,
		/** RN07, item 4: depois do momento do lançamento. */
		HORARIO_FUTURO,
		/** RN07, item 4: antes ou igual à última movimentação confirmada. */
		HORARIO_ANTERIOR
	}

	/** Registro manual recusado, com o motivo e, se for o caso, a ficha atual do aluno. */
	public static class RegistroManualRecusado extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final Motivo motivo;

		private final transient Ficha ficha;

		RegistroManualRecusado(Motivo motivo, Ficha ficha) {
			super(motivo.name(), null, false, false);
			this.motivo = motivo;
			this.ficha = ficha;
		}

		public Motivo getMotivo() {
			return motivo;
		}

		/** A situação do aluno no momento da recusa; nula se ele não existe. */
		public Ficha getFicha() {
			return ficha;
		}
	}

	/**
	 * O que a tela mostra do aluno (RF12, passo 4), com o tipo calculado agora.
	 *
	 * @param tipo              nulo se não é possível registrar
	 * @param ultimaConfirmada  instante da última movimentação confirmada; nulo se não há
	 * @param liberacaoPendente há uma liberação {@code AGUARDANDO_GIRO} para o aluno
	 */
	public record Ficha(long idAluno, String nome, String primeiroNome, String matricula, String turma,
			boolean podeRegistrar, TipoMovimentacao tipo, Instant ultimaConfirmada, boolean liberacaoPendente) {
	}

	/** O registro gravado (ou o original, num reenvio). */
	public record Registrado(long idRegistro, long idAluno, TipoMovimentacao tipo, String primeiroNome,
			Instant ocorridoEm) {
	}

	private final RegistrosDaPortaria registros;

	private final TipoAutomatico tipoAutomatico;

	private final Expiracoes expiracoes;

	private final Auditoria auditoria;

	public RegistroManual(RegistrosDaPortaria registros, TipoAutomatico tipoAutomatico, Expiracoes expiracoes,
			Auditoria auditoria) {
		this.registros = registros;
		this.tipoAutomatico = tipoAutomatico;
		this.expiracoes = expiracoes;
		this.auditoria = auditoria;
	}

	/** A situação atual do aluno, para o formulário e a revisão. Vazia se ele não existe. */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public Optional<Ficha> ficha(long idAluno) {
		expiracoes.expirarVencidas();
		return registros.alunoDoManual(idAluno, expiracoes.agora()).map(this::ficha);
	}

	/**
	 * RN07, item 4, na tela e na gravação.
	 *
	 * @return o problema do horário, ou vazio se ele serve
	 */
	public Optional<Motivo> problemaNoHorario(Ficha ficha, Instant ocorrido, Instant agora) {
		if (ocorrido.isAfter(agora)) {
			return Optional.of(Motivo.HORARIO_FUTURO);
		}
		if (ficha.ultimaConfirmada() != null && !ocorrido.isAfter(ficha.ultimaConfirmada())) {
			return Optional.of(Motivo.HORARIO_ANTERIOR);
		}
		return Optional.empty();
	}

	/**
	 * RF12, passo 6.
	 *
	 * @param tipoEsperado o tipo que a tela mostrou; só é comparado, nunca gravado (RN01)
	 * @throws RegistroManualRecusado sem gravar nada
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = RegistroManualRecusado.class)
	public Registrado registrar(long idAluno, Instant ocorrido, String motivo, TipoMovimentacao tipoEsperado,
			UUID chaveRequisicao, String login) {
		expiracoes.expirarVencidas();
		Instant agora = expiracoes.agora();
		if (registros.alunoDoManual(idAluno, agora).isEmpty()) {
			throw new RegistroManualRecusado(Motivo.ALUNO_NAO_ENCONTRADO, null);
		}
		registros.bloquearAluno(idAluno);

		// RN08: o reenvio devolve o registro original, sem gravar outro
		Optional<Manual> original = registros.manualPorChave(chaveRequisicao);
		if (original.isPresent() && original.get().idAluno() == idAluno) {
			Manual manual = original.get();
			return new Registrado(manual.idRegistro(), idAluno, manual.tipo(), Liberacao.primeiroNomeDe(manual.nome()),
					manual.ocorridoEm());
		}

		// Lida de novo com o aluno bloqueado: é ela que vale, e não a da tela
		AlunoDoManual aluno = registros.alunoDoManual(idAluno, agora).orElseThrow();
		Ficha ficha = ficha(aluno);
		if (!ficha.podeRegistrar()) {
			throw new RegistroManualRecusado(Motivo.SEM_REGISTRO_POSSIVEL, ficha);
		}
		if (ficha.liberacaoPendente()) {
			throw new RegistroManualRecusado(Motivo.LIBERACAO_PENDENTE, ficha);
		}
		if (ficha.tipo() != tipoEsperado) {
			throw new RegistroManualRecusado(Motivo.SITUACAO_MUDOU, ficha);
		}
		Optional<Motivo> horario = problemaNoHorario(ficha, ocorrido, agora);
		if (horario.isPresent()) {
			throw new RegistroManualRecusado(horario.get(), ficha);
		}

		long id = registros.gravarManual(new Vinculo(aluno.idVinculo(), aluno.turma()), registros.idDoUsuario(login),
				ficha.tipo(), ocorrido, agora, motivo, chaveRequisicao);
		// Só IDs e o tipo: o motivo é texto livre e não vai para o log (A10 §8.15)
		auditoria.registrar(EventoAuditoria.REGISTRO_MANUAL,
				detalhes().id("registro", id).id("aluno", idAluno).texto("tipo", ficha.tipo().name()));
		return new Registrado(id, idAluno, ficha.tipo(), ficha.primeiroNome(), ocorrido);
	}

	private Ficha ficha(AlunoDoManual aluno) {
		boolean podeRegistrar = aluno.ativo() && aluno.idVinculo() != null;
		Optional<UltimaMovimentacao> ultima = registros.ultimaConfirmada(aluno.id());
		return new Ficha(aluno.id(), aluno.nome(), Liberacao.primeiroNomeDe(aluno.nome()), aluno.matricula(),
				aluno.turma(), podeRegistrar, podeRegistrar ? tipo(ultima) : null,
				ultima.map(UltimaMovimentacao::ocorridoEm).orElse(null),
				registros.liberacaoPendente(aluno.id()).isPresent());
	}

	/** RN01; RF12, passo 3: a saída pendente de dia anterior gera uma SAIDA (RN06). */
	private TipoMovimentacao tipo(Optional<UltimaMovimentacao> ultima) {
		Decisao decisao = tipoAutomatico.decidir(ultima);
		return decisao.recusada() ? TipoMovimentacao.SAIDA : decisao.tipo();
	}

}
