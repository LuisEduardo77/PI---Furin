package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.util.DigestUtils;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Medição automatizada do RNF02 no servidor (T-33; CA-RNF02-02 e 04; DEC-28, G4): com a
 * carga de referência (scripts/desempenho/carga-800-alunos.sql: 800 alunos e 30 dias de
 * movimentações), faz 30 leituras seguidas e, depois de cada uma, o giro simulado, e
 * falha se alguma resposta passar de {@code furin.portaria.tempo-resposta-max} (2 s).
 * <p>
 * Mede o tempo do pedido dentro da aplicação (MockMvc), sem a rede e sem o navegador: é o
 * teste INT do RNF02, que **não substitui** a medição na tela (scripts/desempenho/
 * medir-portaria.js; docs/evidencias/rnf02-medicao.md). O resultado vai para
 * {@code target/rnf02-medicao-servidor.md}.
 * <p>
 * Sem a transação do teste e com o relógio de verdade, como na aplicação: a carga é gravada
 * antes e apagada depois (apagar-carga-800-alunos.sql).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
class DesempenhoDaPortariaTests {

	private static final String FUNCIONARIO = "desempenho.medicao";
	private static final int RODADA = 30;
	private static final Path CARGA = Path.of("scripts/desempenho/carga-800-alunos.sql");
	private static final Path LIMPEZA = Path.of("scripts/desempenho/apagar-carga-800-alunos.sql");
	private static final Path RELATORIO = Path.of("target/rnf02-medicao-servidor.md");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private DataSource banco;

	@Autowired
	private FurinProperties propriedades;

	@BeforeEach
	void carregar() throws SQLException {
		apagar();
		try (Connection conexao = banco.getConnection()) {
			ScriptUtils.executeSqlScript(conexao, new FileSystemResource(CARGA));
		}
		new CenaDaPortaria(jdbc).funcionario(FUNCIONARIO, Instant.now().minus(Duration.ofDays(1)));
	}

	@AfterEach
	void apagar() throws SQLException {
		try (Connection conexao = banco.getConnection()) {
			ScriptUtils.executeSqlScript(conexao, new FileSystemResource(LIMPEZA));
		}
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	@Test
	void trintaLeiturasETrintaGirosRespondemEmAteDoisSegundos() throws Exception {
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula LIKE 'DES____'", Integer.class))
				.isEqualTo(800);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso r JOIN vinculo_turma v "
				+ "ON v.id_vinculo = r.id_vinculo JOIN aluno a ON a.id_aluno = v.id_aluno "
				+ "WHERE a.matricula LIKE 'DES____'", Integer.class)).isEqualTo(800 * 30 * 2);

		List<Long> leituras = new ArrayList<>();
		List<Long> giros = new ArrayList<>();
		for (int i = 1; i <= RODADA; i++) {
			// Alunos espalhados pela carga (de 26 em 26), cada um lido uma vez
			String matricula = "DES%04d".formatted(1 + (i - 1) * 26);
			String codigo = DigestUtils.md5DigestAsHex(("furin-rnf02-" + matricula).getBytes(StandardCharsets.UTF_8));

			long inicio = System.nanoTime();
			MockHttpServletResponse leitura = mvc.perform(post("/portaria/leituras").with(funcionario()).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
					.andReturn().getResponse();
			leituras.add(System.nanoTime() - inicio);
			assertThat(leitura.getStatus()).as("leitura de %s", matricula).isEqualTo(201);
			String idLiberacao = leitura.getContentAsString().split("\"idLiberacao\":", 2)[1].split("\\D", 2)[0];

			inicio = System.nanoTime();
			MockHttpServletResponse giro = mvc.perform(post("/portaria/giros").with(funcionario()).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"idLiberacao\":" + idLiberacao + ",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
					.andReturn().getResponse();
			giros.add(System.nanoTime() - inicio);
			assertThat(giro.getStatus()).as("giro de %s", matricula).isEqualTo(200);
		}

		escreverRelatorio(leituras, giros);
		long limite = propriedades.portaria().tempoRespostaMax().toNanos();
		assertThat(leituras).as("CA-RNF02-02: todas as leituras").allSatisfy(t -> assertThat(t).isLessThanOrEqualTo(limite));
		assertThat(giros).as("CA-RNF02-04: todos os giros").allSatisfy(t -> assertThat(t).isLessThanOrEqualTo(limite));
	}

	private static void escreverRelatorio(List<Long> leituras, List<Long> giros) throws IOException {
		StringBuilder md = new StringBuilder()
				.append("# RNF02 — medição no servidor (DesempenhoDaPortariaTests)\n\n")
				.append("Executado em ").append(Instant.now()).append(" (UTC). Java ")
				.append(System.getProperty("java.version")).append(", ").append(System.getProperty("os.name"))
				.append(".\n\n| # | Leitura (ms) | Giro (ms) |\n|---|---|---|\n");
		for (int i = 0; i < leituras.size(); i++) {
			md.append("| ").append(i + 1).append(" | ").append(ms(leituras.get(i))).append(" | ")
					.append(ms(giros.get(i))).append(" |\n");
		}
		md.append("| **Máximo** | ").append(ms(maximo(leituras))).append(" | ").append(ms(maximo(giros))).append(" |\n")
				.append("| **Média** | ").append(ms(media(leituras))).append(" | ").append(ms(media(giros))).append(" |\n");
		Files.createDirectories(RELATORIO.getParent());
		Files.writeString(RELATORIO, md, StandardCharsets.UTF_8);
	}

	private static long maximo(List<Long> tempos) {
		return tempos.stream().mapToLong(Long::longValue).max().orElse(0);
	}

	private static long media(List<Long> tempos) {
		return (long) tempos.stream().mapToLong(Long::longValue).average().orElse(0);
	}

	private static String ms(long nanos) {
		return "%.1f".formatted(nanos / 1_000_000.0).replace('.', ',');
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
