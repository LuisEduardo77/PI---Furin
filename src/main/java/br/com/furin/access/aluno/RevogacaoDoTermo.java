package br.com.furin.access.aluno;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.aluno.RegistrosDeAlunos.Consentimento;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;

/**
 * Registro da revogação do termo (T-20; RF05, fluxo A2; DEC-34; RN16): numa única
 * transação, com o aluno bloqueado (RN08), grava {@code revogado_em}, inativa o aluno,
 * revoga a carteirinha e inativa os contatos, o que encerra os avisos por e-mail (DEC-37).
 * O aluno continua estudando, com a entrada e a saída controladas fora do sistema.
 */
@Service
public class RevogacaoDoTermo {

	/** Por que a revogação não foi registrada. Nada mudou. */
	public enum MotivoDaRecusa {
		/** Não existe consentimento com esse ID para esse aluno. */
		NAO_ENCONTRADO,
		/** O termo já tinha sido revogado. */
		JA_REVOGADO,
		/** RF05, A2, passo 4: registre a saída manual antes. */
		SAIDA_PENDENTE
	}

	/** A revogação recusada. */
	public static class RevogacaoRecusada extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final MotivoDaRecusa motivo;

		RevogacaoRecusada(MotivoDaRecusa motivo) {
			super(motivo.name(), null, false, false);
			this.motivo = motivo;
		}

		public MotivoDaRecusa getMotivo() {
			return motivo;
		}
	}

	private final RegistrosDeAlunos registros;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	public RevogacaoDoTermo(RegistrosDeAlunos registros, Auditoria auditoria, Clock relogio,
			FurinProperties propriedades) {
		this.registros = registros;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
	}

	/** "Hoje" no fuso da escola (RN10). */
	public LocalDate hoje() {
		return LocalDate.now(relogio.withZone(fusoDaEscola));
	}

	/** O dia da conferência no fuso da escola: a revogação não pode ser anterior a ele. */
	public LocalDate diaDaConferencia(Consentimento consentimento) {
		return consentimento.conferidoEm().atZone(fusoDaEscola).toLocalDate();
	}

	/**
	 * @param dataDaRevogacao já conferida pelo controller: não é futura nem anterior à conferência
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public void registrar(long idAluno, long idConsentimento, LocalDate dataDaRevogacao) {
		registros.bloquearAluno(idAluno);
		Consentimento consentimento = registros.consentimentoDoAluno(idAluno, idConsentimento)
				.orElseThrow(() -> new RevogacaoRecusada(MotivoDaRecusa.NAO_ENCONTRADO));
		if (consentimento.revogadoEm() != null) {
			throw new RevogacaoRecusada(MotivoDaRecusa.JA_REVOGADO);
		}
		if (registros.comSaidaPendente(idAluno)) {
			throw new RevogacaoRecusada(MotivoDaRecusa.SAIDA_PENDENTE);
		}

		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		if (!registros.revogarConsentimento(idConsentimento, instanteDaRevogacao(dataDaRevogacao, agora))) {
			throw new RevogacaoRecusada(MotivoDaRecusa.JA_REVOGADO);
		}
		registros.inativarAluno(idAluno);
		registros.revogarCarteirinhas(idAluno, agora);
		registros.inativarContatos(idAluno);
		// CA-RF05-08: só o evento e o ID do consentimento, nada do conteúdo do termo
		auditoria.registrar(EventoAuditoria.CONSENTIMENTO_REVOGADO, detalhes().id("consentimento", idConsentimento));
	}

	/**
	 * {@code revogado_em} guarda data e hora; o funcionário informa só a data. Vale o fim desse
	 * dia, ou o instante atual se a data for hoje: o último instante do dia que já passou [PROP].
	 */
	private Instant instanteDaRevogacao(LocalDate data, Instant agora) {
		if (data.equals(hoje())) {
			return agora;
		}
		return data.atTime(LocalTime.MAX).atZone(fusoDaEscola).toInstant().truncatedTo(ChronoUnit.MICROS);
	}

}
