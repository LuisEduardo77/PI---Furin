package br.com.furin.access.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Carrega o application.yml de verdade, troca um parâmetro por vez por um valor
 * inválido e confere que a aplicação não sobe (T-07, Bean Validation).
 * Não usa o banco.
 */
class FurinPropertiesValidacaoTests {

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(FurinProperties.class)
	static class SoPropriedades {
	}

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withInitializer(new ConfigDataApplicationContextInitializer())
			.withUserConfiguration(SoPropriedades.class);

	@Test
	void aceitaOsValoresAprovados() {
		contexto.run(ctx -> {
			assertThat(ctx).hasNotFailed();
			assertThat(ctx).hasSingleBean(FurinProperties.class);
		});
	}

	static String[] valoresInvalidos() {
		Path dentroDoProjeto = Path.of("").toAbsolutePath().resolve("src/main/resources/static");
		return new String[] {
				"furin.escola.nome= ",
				"furin.portaria.prazo-giro=0s",
				"furin.portaria.janela-repeticao=-2s",
				"furin.carteirinha.tentativas-colisao=0",
				"furin.busca.max-resultados=0",
				"furin.historico.periodo-max-dias=-1",
				"furin.login.bloqueio=",
				"furin.senha.tamanho-minimo=0",
				"furin.sessao.aviso-antes=10s",
				"furin.sessao.aviso-antes=30m",
				"furin.termo.versao-atual=v1-2026-versao-longa-demais-xyz",
				"furin.arquivos.diretorio=arquivos",
				"furin.arquivos.diretorio=" + dentroDoProjeto,
				"furin.arquivos.foto.tipos=",
				"furin.arquivos.termo.tamanho-max=0B",
				"furin.retencao.anos=0"
		};
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("valoresInvalidos")
	void recusaValorInvalido(String propriedade) {
		String chave = propriedade.substring(0, propriedade.indexOf('='));
		String grupo = chave.substring(0, chave.lastIndexOf('.'));
		contexto.withPropertyValues(propriedade).run(ctx -> {
			assertThat(ctx).hasFailed();
			Throwable falha = ctx.getStartupFailure();
			assertThat(causas(falha)).anyMatch(BindException.class::isInstance);
			assertThat(falha).hasStackTraceContaining(grupo);
		});
	}

	private static java.util.List<Throwable> causas(Throwable falha) {
		java.util.List<Throwable> causas = new java.util.ArrayList<>();
		for (Throwable t = falha; t != null && !causas.contains(t); t = t.getCause()) {
			causas.add(t);
		}
		return causas;
	}

}
