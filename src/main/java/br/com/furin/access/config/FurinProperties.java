package br.com.furin.access.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Parâmetros de regra do Furin Access (prefixo {@code furin}), conforme a tabela
 * "Parâmetros" de arquitetura-e-dados.md §4 (T-07).
 * <p>
 * Nenhum valor fica no código: todos vêm do application.yml (ou do ambiente) e a
 * aplicação não sobe se algum faltar ou for inválido.
 */
@Validated
@ConfigurationProperties(prefix = "furin")
public record FurinProperties(
		@NotNull @Valid Escola escola,
		@NotNull @Valid Portaria portaria,
		@NotNull @Valid Carteirinha carteirinha,
		@NotNull @Valid Presentes presentes,
		@NotNull @Valid Busca busca,
		@NotNull @Valid Historico historico,
		@NotNull @Valid Relatorio relatorio,
		@NotNull @Valid Senha senha,
		@NotNull @Valid Login login,
		@NotNull @Valid Sessao sessao,
		@NotNull @Valid Notificacao notificacao,
		@NotNull @Valid Termo termo,
		@NotNull @Valid Arquivos arquivos,
		@NotNull @Valid Retencao retencao) {

	/** RF20. No repositório, só valor fictício (A10 §8.33). */
	public record Escola(@NotBlank String nome) {
	}

	/** DEC-02, A08 RNF02, DEC-28 (A1, A6, A7). */
	public record Portaria(
			@NotNull @DurationMin(millis = 1) Duration janelaRepeticao,
			@NotNull @DurationMin(millis = 1) Duration tempoRespostaMax,
			@NotNull @DurationMin(millis = 1) Duration prazoGiro,
			@NotNull @DurationMin(millis = 1) Duration monitorExibicao,
			@NotNull @DurationMin(millis = 1) Duration painelLimpeza) {
	}

	/** RF06; DEC-28 (D7). */
	public record Carteirinha(@Min(1) int tentativasColisao) {
	}

	/** RF16; DEC-28 (C2). */
	public record Presentes(@NotNull @DurationMin(seconds = 1) Duration intervaloAtualizacao) {
	}

	/** RF11; DEC-28 (C4). */
	public record Busca(@Min(1) int maxResultados, @Min(1) int tamanhoMinimoNome) {
	}

	/** RF18; DEC-28 (C5). */
	public record Historico(@Min(1) int tamanhoPagina, @Min(1) int periodoMaxDias) {
	}

	/** RF20; DEC-28 (C6). */
	public record Relatorio(@Min(1) int maxRegistros) {
	}

	/** DEC-21; DEC-28 (E4). */
	public record Senha(
			@NotNull @DurationMin(minutes = 1) Duration validadeTemporaria,
			@Min(1) int tamanhoTemporaria,
			@Min(1) int tamanhoMinimo) {
	}

	/** DEC-28 (E2). */
	public record Login(@Min(1) int maxTentativas, @NotNull @DurationMin(seconds = 1) Duration bloqueio) {
	}

	/** DEC-28 (E2); A11 §5.16.2. */
	public record Sessao(
			@NotNull @DurationMin(minutes = 1) Duration inatividade,
			// A11 §5.16.2: o aviso antes de a sessão expirar dá pelo menos 20 s
			@NotNull @DurationMin(seconds = 20) Duration avisoAntes) {

		@AssertTrue(message = "o aviso deve vir antes do fim da sessão (aviso-antes menor que inatividade)")
		public boolean isAvisoAntesDaInatividade() {
			return inatividade == null || avisoAntes == null || avisoAntes.compareTo(inatividade) < 0;
		}
	}

	/** DEC-28 (F1). Só vale numa evolução com envio real (DEC-32). */
	public record Notificacao(@Min(1) int maxTentativas) {
	}

	/** RF05; DEC-26. O tamanho segue consentimento.versao_termo (VARCHAR(30)). */
	public record Termo(@NotBlank @Size(max = 30) String versaoAtual) {
	}

	/** DEC-08; DEC-28 (D1, D2). */
	public record Arquivos(
			@NotNull Path diretorio,
			@NotNull @Valid RegraArquivo foto,
			@NotNull @Valid RegraArquivo termo) {

		/** DEC-08: a área privada fica fora do projeto (e, portanto, fora de static/). */
		@AssertTrue(message = "o diretório de arquivos deve ser um caminho absoluto fora do projeto")
		public boolean isDiretorioForaDoProjeto() {
			if (diretorio == null) {
				return true;
			}
			Path projeto = Path.of("").toAbsolutePath().normalize();
			return diretorio.isAbsolute() && !diretorio.normalize().startsWith(projeto);
		}
	}

	/** Tipos aceitos (MIME) e tamanho máximo de um arquivo enviado. */
	public record RegraArquivo(
			@NotEmpty List<@NotBlank String> tipos,
			@NotNull DataSize tamanhoMax) {

		@AssertTrue(message = "o tamanho máximo deve ser maior que zero")
		public boolean isTamanhoMaxPositivo() {
			return tamanhoMax == null || tamanhoMax.toBytes() > 0;
		}
	}

	/** DEC-36. O descarte automático fica fora do MVP. */
	public record Retencao(@Min(1) int anos) {
	}

}
