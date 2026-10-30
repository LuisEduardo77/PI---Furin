package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.unit.DataSize;

/**
 * Sobe a aplicação e confere que os valores aprovados da tabela "Parâmetros"
 * (arquitetura-e-dados.md §4) chegam à {@link FurinProperties} (T-07).
 */
@SpringBootTest
@ActiveProfiles("test")
class FurinPropertiesTests {

	@Autowired
	private FurinProperties furin;

	@Test
	void escolaTemSoNomeFicticio() {
		assertThat(furin.escola().nome()).isEqualTo("Escola Exemplo");
	}

	@Test
	void carregaOsTemposDaPortaria() {
		FurinProperties.Portaria portaria = furin.portaria();
		assertThat(portaria.janelaRepeticao()).isEqualTo(Duration.ofSeconds(2));
		assertThat(portaria.tempoRespostaMax()).isEqualTo(Duration.ofSeconds(2));
		assertThat(portaria.prazoGiro()).isEqualTo(Duration.ofSeconds(15));
		assertThat(portaria.monitorExibicao()).isEqualTo(Duration.ofSeconds(4));
		assertThat(portaria.painelLimpeza()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void carregaOsLimitesDeConsulta() {
		assertThat(furin.carteirinha().tentativasColisao()).isEqualTo(3);
		assertThat(furin.presentes().intervaloAtualizacao()).isEqualTo(Duration.ofSeconds(30));
		assertThat(furin.busca().maxResultados()).isEqualTo(20);
		assertThat(furin.busca().tamanhoMinimoNome()).isEqualTo(2);
		assertThat(furin.historico().tamanhoPagina()).isEqualTo(50);
		assertThat(furin.historico().periodoMaxDias()).isEqualTo(31);
		assertThat(furin.relatorio().maxRegistros()).isEqualTo(1000);
	}

	@Test
	void carregaSenhaLoginESessao() {
		assertThat(furin.senha().validadeTemporaria()).isEqualTo(Duration.ofHours(72));
		assertThat(furin.senha().tamanhoTemporaria()).isEqualTo(12);
		assertThat(furin.senha().tamanhoMinimo()).isEqualTo(8);
		assertThat(furin.login().maxTentativas()).isEqualTo(5);
		assertThat(furin.login().bloqueio()).isEqualTo(Duration.ofMinutes(15));
		assertThat(furin.sessao().inatividade()).isEqualTo(Duration.ofMinutes(30));
		assertThat(furin.sessao().avisoAntes()).isEqualTo(Duration.ofSeconds(60));
	}

	@Test
	void carregaNotificacaoTermoERetencao() {
		assertThat(furin.notificacao().maxTentativas()).isEqualTo(3);
		assertThat(furin.termo().versaoAtual()).isEqualTo("v1-2026");
		assertThat(furin.retencao().anos()).isEqualTo(5);
	}

	@Test
	void carregaAsRegrasDeArquivo() {
		FurinProperties.Arquivos arquivos = furin.arquivos();
		assertThat(arquivos.foto().tipos()).containsExactly("image/jpeg", "image/png");
		assertThat(arquivos.foto().tamanhoMax()).isEqualTo(DataSize.ofMegabytes(2));
		assertThat(arquivos.termo().tipos()).containsExactly("application/pdf", "image/jpeg", "image/png");
		assertThat(arquivos.termo().tamanhoMax()).isEqualTo(DataSize.ofMegabytes(5));
	}

	@Test
	void diretorioDeArquivosFicaForaDoProjeto() {
		Path diretorio = furin.arquivos().diretorio();
		assertThat(diretorio).isAbsolute()
				.isEqualTo(Path.of(System.getProperty("user.home"), "furin-access", "arquivos"));
		assertThat(diretorio.normalize().startsWith(Path.of("").toAbsolutePath().normalize())).isFalse();
	}

}
