package br.com.furin.access.exemplo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.assertj.core.data.Offset;
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

	/** Seletor → bloco de declarações, na ordem do arquivo. */
	private static final Map<String, String> regras = new LinkedHashMap<>();

	/** Variáveis do :root, sem o "--" (ex.: "cor-texto" → "#172B3A"). */
	private static final Map<String, String> variaveis = new LinkedHashMap<>();

	@BeforeAll
	static void lerCss() throws IOException {
		String css = semComentarios(new ClassPathResource("static/css/base.css")
				.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
		for (String bloco : css.split("}")) {
			int abre = bloco.indexOf('{');
			if (abre >= 0) {
				regras.put(bloco.substring(0, abre).trim(), bloco.substring(abre + 1));
			}
		}
		for (String declaracao : declaracoes(":root").split(";")) {
			String[] partes = declaracao.trim().split(":", 2);
			if (partes.length == 2 && partes[0].startsWith("--")) {
				variaveis.put(partes[0].substring(2), partes[1].trim());
			}
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
		assertThat(contraste("#596B7A", "#FFFFFF")).isCloseTo(5.51, Offset.offset(0.01));
		assertThat(cor("cor-texto")).isEqualToIgnoringCase("#172B3A");
		assertThat(cor("cor-primaria")).isEqualToIgnoringCase("#173F5F");
	}

	@Test
	void focoVisivelTem3pxComAfastamento() {
		assertThat(declaracoes(":focus-visible"))
				.contains("outline: 3px solid var(--cor-foco)")
				.contains("outline-offset: 2px");
	}

	@Test
	void alvoMinimoTem44px() {
		assertThat(variaveis).containsEntry("alvo-minimo", "2.75rem");
		assertThat(2.75 * 16).isGreaterThanOrEqualTo(44);
		assertThat(declaracoes(".menu__lista a")).contains("min-height: var(--alvo-minimo)")
				.contains("min-width: var(--alvo-minimo)");
		assertThat(declaracoes(".botao,\nbutton")).contains("min-height: var(--alvo-minimo)")
				.contains("min-width: var(--alvo-minimo)");
	}

	/** px só em bordas, contorno de foco e na classe visualmente-oculto (RNF01; A11 §5.12.7). */
	@Test
	void usaUnidadesRelativas() {
		regras.forEach((seletor, corpo) -> {
			if (seletor.equals(".visualmente-oculto")) {
				return;
			}
			for (String declaracao : corpo.split(";")) {
				if (temPx(declaracao)) {
					String propriedade = declaracao.trim().split(":", 2)[0];
					assertThat(propriedade.startsWith("outline") || propriedade.startsWith("border"))
							.as("px em '%s' de %s", declaracao.trim(), seletor).isTrue();
				}
			}
		});
		assertThat(declaracoes("html")).contains("font-size: 100%");
	}

	/** Um número seguido de "px" (ex.: "3px"), sem expressão regular. */
	private static boolean temPx(String texto) {
		for (int i = texto.indexOf("px"); i >= 0; i = texto.indexOf("px", i + 2)) {
			if (i > 0 && Character.isDigit(texto.charAt(i - 1))) {
				return true;
			}
		}
		return false;
	}

	private static String semComentarios(String css) {
		StringBuilder limpo = new StringBuilder();
		int inicio = 0;
		for (int abre = css.indexOf("/*"); abre >= 0; abre = css.indexOf("/*", inicio)) {
			limpo.append(css, inicio, abre);
			int fecha = css.indexOf("*/", abre + 2);
			inicio = fecha < 0 ? css.length() : fecha + 2;
		}
		return limpo.append(css.substring(inicio)).toString();
	}

	private static String declaracoes(String seletor) {
		String corpo = regras.get(seletor);
		assertThat(corpo).as("regra %s no base.css", seletor).isNotNull();
		return corpo;
	}

	private static String cor(String nome) {
		String valor = variaveis.get(nome);
		assertThat(valor).as("variável --%s", nome).isNotNull().startsWith("#").hasSize(7);
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
