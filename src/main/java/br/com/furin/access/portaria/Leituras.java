package br.com.furin.access.portaria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.portaria.RegistrosDaPortaria.Aluno;
import br.com.furin.access.portaria.RegistrosDaPortaria.Carteirinha;
import br.com.furin.access.portaria.RegistrosDaPortaria.Vinculo;
import br.com.furin.access.portaria.TipoAutomatico.Decisao;

/**
 * Leitura da carteirinha (T-26; RF09, RF14, passo 3; RN01, RN02, RN06, RN08, RN09).
 * <p>
 * Numa transação: encontra a carteirinha, <b>bloqueia o aluno</b>
 * ({@code SELECT … FOR UPDATE}), devolve a liberação original se a
 * {@code chaveRequisicao} já foi usada (reenvio), valida, calcula o tipo (RN01) e grava a
 * liberação {@code AGUARDANDO_GIRO}. A transação é {@code READ_COMMITTED}: depois do
 * bloqueio, cada consulta vê o que outra operação sobre o mesmo aluno acabou de gravar.
 * <p>
 * Repetições (T-27; RF15, na ordem do fluxo principal): a leitura do mesmo código na
 * janela de 2 s é ignorada (RN03); com o aluno já aguardando o giro, a leitura devolve a
 * mesma liberação, sem renovar o prazo (RN04); com outro aluno aguardando, é recusada
 * (RN05). A janela é calculada no banco, e o giro a encerra porque deixa a liberação
 * {@code CONFIRMADA} (DEC-02). Ficam para depois a expiração de 15 s (T-29) e os textos
 * das recusas (T-31).
 */
@Service
public class Leituras {

	/** O que aconteceu com a leitura. Só {@link #NOVA} grava alguma coisa. */
	public enum Desfecho {
		/** Liberação criada agora, ou reenvio com a mesma chave (RN08). */
		NOVA,
		/** RN03: o mesmo código foi lido há até 2 s, sem giro depois. "Leitura já recebida". */
		JA_RECEBIDA,
		/** RN04: o aluno já tem esta liberação pendente. "Aguardando passagem". */
		AGUARDANDO_PASSAGEM
	}

	/**
	 * @param liberacao a liberação criada ou a que já estava pendente. Só falta (é
	 *                  {@code null}) em {@link Desfecho#JA_RECEBIDA} quando a leitura da
	 *                  janela já não está pendente (cancelada)
	 */
	public record ResultadoDaLeitura(Liberacao liberacao, Desfecho desfecho) {
	}

	/** DEC-46: a restrição que garante uma liberação pendente por vez no ponto de leitura. */
	static final String UMA_LIBERACAO_POR_VEZ = "uk_registro_pendente_ponto";

	private final Duration janelaDeRepeticao;

	/** RN15: {@code furin.carteirinha.tamanho-codigo} caracteres hexadecimais (A14 p.6). */
	private final Pattern formatoDoCodigo;

	private final RegistrosDaPortaria registros;

	private final TipoAutomatico tipoAutomatico;

	private final Auditoria auditoria;

	private final Clock relogio;

	public Leituras(RegistrosDaPortaria registros, TipoAutomatico tipoAutomatico, Auditoria auditoria, Clock relogio,
			FurinProperties propriedades) {
		this.formatoDoCodigo = Pattern.compile("[0-9a-f]{" + propriedades.carteirinha().tamanhoCodigo() + "}");
		this.janelaDeRepeticao = propriedades.portaria().janelaRepeticao();
		this.registros = registros;
		this.tipoAutomatico = tipoAutomatico;
		this.auditoria = auditoria;
		this.relogio = relogio;
	}

	/**
	 * @param codigo          como chegou do campo de leitura; o tipo nunca vem junto (RN01)
	 * @param chaveRequisicao UUID gerado pelo navegador (RN08)
	 * @param login           o funcionário da sessão, gravado em {@code id_usuario}
	 */
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ResultadoDaLeitura ler(String codigo, UUID chaveRequisicao, String login) {
		// DATETIME(6) guarda microssegundos: o instante gravado é o mesmo que volta
		Instant agora = relogio.instant().truncatedTo(ChronoUnit.MICROS);
		String normalizado = codigo.strip().toLowerCase(Locale.ROOT);
		if (!formatoDoCodigo.matcher(normalizado).matches()) {
			throw recusar(normalizado, MotivoDaRecusa.CODIGO_NAO_RECONHECIDO, null);
		}
		Carteirinha carteirinha = registros.carteirinhaPorCodigo(normalizado)
				.orElseThrow(() -> recusar(normalizado, MotivoDaRecusa.CODIGO_NAO_RECONHECIDO, null));

		Aluno aluno = registros.bloquearAluno(carteirinha.idAluno());

		// RN08: reenvio com a mesma chave devolve a liberação original, sem criar outra
		Optional<Long> jaRegistrada = registros.registroPorChave(chaveRequisicao);
		if (jaRegistrada.isPresent()) {
			return new ResultadoDaLeitura(registros.liberacao(jaRegistrada.get()).orElseThrow(), Desfecho.NOVA);
		}

		// RN03 (DEC-02): o mesmo código há até 2 s, sem giro depois, é ignorado. Nada é gravado
		Optional<Liberacao> naJanela = registros.leituraNaJanela(carteirinha.id(), agora.minus(janelaDeRepeticao));
		if (naJanela.isPresent()) {
			Liberacao recebida = naJanela.get();
			return new ResultadoDaLeitura(RegistrosDaPortaria.AGUARDANDO_GIRO.equals(recebida.situacao()) ? recebida : null,
					Desfecho.JA_RECEBIDA);
		}

		if (carteirinha.revogadaEm() != null) {
			throw recusar(normalizado, MotivoDaRecusa.CARTEIRINHA_REVOGADA, null);
		}
		if (carteirinha.expiraEm() != null && !agora.isBefore(carteirinha.expiraEm())) {
			throw recusar(normalizado, MotivoDaRecusa.CARTEIRINHA_EXPIRADA, null);
		}
		if (!aluno.ativo()) {
			throw recusar(normalizado, MotivoDaRecusa.ALUNO_INATIVO, null);
		}
		Vinculo vinculo = registros.vinculoVigente(aluno.id(), agora)
				.orElseThrow(() -> recusar(normalizado, MotivoDaRecusa.SEM_TURMA_VIGENTE, null));
		// RN04: com liberação pendente, nada é gravado e o prazo não é renovado. Devolver a
		// liberação permite confirmar o giro mesmo se a tela a perdeu (página recarregada)
		Optional<Liberacao> pendente = registros.liberacaoPendente(aluno.id());
		if (pendente.isPresent()) {
			return new ResultadoDaLeitura(pendente.get(), Desfecho.AGUARDANDO_PASSAGEM);
		}
		// RN05: um ponto de leitura, uma liberação por vez. A pendente só pode ser de outro aluno
		if (registros.existeLiberacaoPendente()) {
			throw recusar(normalizado, MotivoDaRecusa.OUTRA_LIBERACAO_PENDENTE, null);
		}

		Decisao decisao = tipoAutomatico.decidir(registros.ultimaConfirmada(aluno.id()));
		if (decisao.recusada()) {
			throw recusar(normalizado, MotivoDaRecusa.SAIDA_PENDENTE, decisao.saidaPendenteDe());
		}

		long id;
		try {
			id = registros.gravarLiberacao(vinculo, registros.idDoUsuario(login), carteirinha.id(), decisao.tipo(),
					agora, chaveRequisicao);
		} catch (DuplicateKeyException corrida) {
			// DEC-46: outro aluno gravou a liberação dele entre a conferência e esta gravação
			if (String.valueOf(corrida.getMessage()).contains(UMA_LIBERACAO_POR_VEZ)) {
				throw recusar(normalizado, MotivoDaRecusa.OUTRA_LIBERACAO_PENDENTE, null);
			}
			throw corrida;
		}
		return new ResultadoDaLeitura(registros.liberacao(id).orElseThrow(), Desfecho.NOVA);
	}

	/** RN09: nada é gravado, e a recusa vai para o log só com os 4 primeiros caracteres do código. */
	private LeituraRecusada recusar(String codigo, MotivoDaRecusa motivo, LocalDate saidaPendenteDe) {
		auditoria.registrar(EventoAuditoria.LEITURA_RECUSADA,
				detalhes().codigoCarteirinha(codigo).texto("motivo", motivo.name()));
		return new LeituraRecusada(motivo, saidaPendenteDe);
	}

}
