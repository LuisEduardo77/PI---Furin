package br.com.furin.access.exemplo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;

/**
 * Confere o static/css/base.css (T-08; RNF01, RNF06): contraste da paleta pela fórmula
 * da WCAG 2.2, foco de 3 px, alvos de 44 px e unidades relativas.
 */
class EstiloBaseTests {

	private static final Pattern REGRA = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
	private static final Pattern VARIAVEL = Pattern.compile("--([a-z-]+):\\s*([^;]+);");

	private static String css;
	private static final Map<String, String> cores = new HashMap<>();

	@BeforeAll
	static void lerCss() throws IOException {
		String bruto = new ClassPathResource("static/css/base.css").getContentAsString(StandardCharsets.UTF_8);
		css = bruto.replace("\r\n", "\n").replaceAll("(?s)/\\*.*?\\*/", "");
		Matcher variavel = VARIAVEL.matcher(css);
		while (variavel.find()) {
			cores.put(variavel.group(1), variavel.group(2).trim());
		}
	}

	/** Pares usados no CSS: frente, fundo e o mínimo da A11 (§5.11.3 e §5.11.4). */
	@ParameterizedTest(name = "{0} sobre {1}: pelo menos {2}:1")
	@CsvSource({
			// texto comum: 4,5:1
			"cor-texto,     cor-fundo,       4.5",
			"cor-texto,     cor-fundo-suave, 4.5",
			"cor-primaria,  cor-fundo,       4.5",
			"cor-fundo,     cor-primaria,    4.5",
			"cor-erro,      cor-fundo,       4.5",
			// componentes, bordas e foco: 3:1
			"cor-borda,     cor-fundo,       3.0",
			"cor-borda,     cor-fundo-suave, 3.0",
			"cor-foco,      cor-fundo,       3.0",
			"cor-erro,      cor-fundo-suave, 3.0",
			"cor-sucesso,   cor-fundo-suave, 3.0"
	})
	void paletaTemContrasteMinimo(String frente, String fundo, double minimo) {
		assertThat(contraste(cor(frente), cor(fundo))).isGreaterThanOrEqualTo(minimo);
	}

	@Test
	void paletaDaA11TemOsContrastesDocumentados() {
		// RNF06: bordas #596B7A sobre branco = 5,51:1
		assertThat(contraste("#596B7A", "#FFFFFF")).isCloseTo(5.51, org.assertj.core.data.Offset.offset(0.01));
		assertThat(cor("cor-texto")).isEqualToIgnoringCase("#172B3A");
		assertThat(cor("cor-primaria")).isEqualToIgnoringCase("#173F5F");
	}

	@Test
	void focoVisivelTem3pxComAfastamento() {
		String foco = declaracoes(":focus-visible");
		assertThat(foco).contains("outline: 3px solid var(--cor-foco)").contains("outline-offset: 2px");
	}

	@Test
	void alvoMinimoTem44px() {
		assertThat(cores.get("alvo-minimo")).isEqualTo("2.75rem");
		assertThat(2.75 * 16).isGreaterThanOrEqualTo(44);
		assertThat(declaracoes(".menu__lista a")).contains("min-height: var(--alvo-minimo)")
				.contains("min-width: var(--alvo-minimo)");
		assertThat(declaracoes(".botao,\nbutton")).contains("min-height: var(--alvo-minimo)")
				.contains("min-width: var(--alvo-minimo)");
	}

	/** px só em bordas, contorno de foco e na classe visualmente-oculto (RNF01; A11 §5.12.7). */
	@Test
	void usaUnidadesRelativas() {
		Matcher regra = REGRA.matcher(css);
		while (regra.find()) {
			String seletor = regra.group(1).trim();
			for (String declaracao : regra.group(2).split(";")) {
				if (declaracao.matches("(?s).*\\d+px.*") && !seletor.equals(".visualmente-oculto")) {
					String propriedade = declaracao.trim().split(":")[0];
					assertThat(propriedade).as("px em '%s' de %s", declaracao.trim(), seletor)
							.matches("outline(-offset)?|border(-[a-z]+)?");
				}
			}
		}
		assertThat(css).doesNotContain("vw;").containsPattern("html \\{\\s*font-size: 100%");
	}

	private static String declaracoes(String seletor) {
		Matcher regra = REGRA.matcher(css);
		while (regra.find()) {
			if (regra.group(1).trim().equals(seletor)) {
				return regra.group(2);
			}
		}
		throw new AssertionError("Regra não encontrada: " + seletor);
	}

	private static String cor(String nome) {
		String valor = cores.get(nome);
		assertThat(valor).as("variável --%s", nome).isNotNull().matches("#[0-9A-Fa-f]{6}");
		return valor;
	}

	/** Razão de contraste da WCAG 2.2 (definição de "contrast ratio" e "relative luminance"). */
	static double contraste(String cor1, String cor2) {
		double l1 = luminancia(cor1);
		double l2 = luminancia(cor2);
		return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
	}

	private static double luminancia(String hex) {
		int rgb = Integer.parseInt(hex.substring(1), 16);
		return 0.2126 * canal(rgb >> 16) + 0.7152 * canal(rgb >> 8) + 0.0722 * canal(rgb);
	}

	private static double canal(int valor) {
		double c = (valor & 0xFF) / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

}
