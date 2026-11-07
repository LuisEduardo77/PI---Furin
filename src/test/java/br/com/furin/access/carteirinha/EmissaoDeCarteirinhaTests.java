package br.com.furin.access.carteirinha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.carteirinha.EmissaoDeCarteirinha.CarteirinhaNaoEmitida;
import br.com.furin.access.config.FurinProperties;

/**
 * Código de carteirinha sem repetição (T-21; RF06; CA-RF06-06 e 07; RN15). Cada teste roda
 * na transação do teste, desfeita no fim.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class EmissaoDeCarteirinhaTests {

	private static final String CODIGO_REVOGADO = "a0b1c2d3e4f5061728394a5b6c7d8e9f";
	private static final String CODIGO_NOVO = "f0e1d2c3b4a5968778695a4b3c2d1e0f";

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Auditoria auditoria;

	@Autowired
	private FurinProperties propriedades;

	@Autowired
	private GeradorAleatorioDeCodigo geradorAleatorio;

	private long aluno;

	private final Instant agora = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@BeforeEach
	void preparar() {
		LocalDateTime ontem = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES ('T21-C001', "
				+ "'Davi Teste', 'sem-arquivo.png', 1, ?)", ontem);
		aluno = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = 'T21-C001'", Long.class);
		// Uma carteirinha revogada: o código dela nunca volta a ser usado (RN15)
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em, revogada_em) VALUES (?, ?, ?, ?)", aluno,
				CODIGO_REVOGADO, ontem, ontem);
	}

	@Test
	void bancoRecusaCodigoDeCarteirinhaRevogada() {
		// CA-RF06-06: teste direto na restrição uk_carteirinha_codigo
		assertThatThrownBy(() -> jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (?, ?, ?)",
				aluno, CODIGO_REVOGADO, LocalDateTime.now(ZoneOffset.UTC)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void colisaoComCodigoRevogadoGeraOutroCodigo() {
		// CA-RF06-07: o primeiro código colide, o segundo é gravado
		EmissaoDeCarteirinha emissao = emissaoQueGera(CODIGO_REVOGADO, CODIGO_NOVO);

		long id = emissao.emitir(aluno, agora);

		assertThat(jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_carteirinha = ?", String.class, id))
				.isEqualTo(CODIGO_NOVO);
		assertThat(jdbc.queryForObject("SELECT revogada_em FROM carteirinha WHERE id_carteirinha = ?",
				LocalDateTime.class, id)).isNull();
	}

	@Test
	void saoAteTresNovasTentativas() {
		// DEC-28, D7: a primeira e mais três (furin.carteirinha.tentativas-colisao). A última vale
		EmissaoDeCarteirinha emissao = emissaoQueGera(CODIGO_REVOGADO, CODIGO_REVOGADO, CODIGO_REVOGADO, CODIGO_NOVO);

		long id = emissao.emitir(aluno, agora);

		assertThat(jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_carteirinha = ?", String.class, id))
				.isEqualTo(CODIGO_NOVO);
	}

	@Test
	void colisaoEmTodasAsTentativasRecusaEVaiParaOLogSemOCodigo(CapturedOutput saida) {
		// RF06, exceções: o cadastro é recusado (quem chama desfaz a transação)
		EmissaoDeCarteirinha emissao = emissaoQueGera(CODIGO_REVOGADO, CODIGO_REVOGADO, CODIGO_REVOGADO,
				CODIGO_REVOGADO, CODIGO_NOVO);

		assertThatThrownBy(() -> emissao.emitir(aluno, agora)).isInstanceOf(CarteirinhaNaoEmitida.class);

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM carteirinha WHERE id_aluno = ?", Integer.class, aluno))
				.isEqualTo(1);
		assertThat(saida.getOut()).contains("evento=CARTEIRINHA_NAO_EMITIDA", "aluno=" + aluno)
				.doesNotContain(CODIGO_REVOGADO);
	}

	@Test
	void geradorAleatorioUsaOTamanhoConfiguradoEmHexadecimal() {
		// RN15: 32 caracteres hexadecimais (furin.carteirinha.tamanho-codigo), sem dado pessoal
		String codigo = geradorAleatorio.gerar();

		assertThat(codigo).hasSize(propriedades.carteirinha().tamanhoCodigo()).matches("[0-9a-f]+");
		assertThat(geradorAleatorio.gerar()).isNotEqualTo(codigo);
	}

	private EmissaoDeCarteirinha emissaoQueGera(String... codigos) {
		Deque<String> fila = new ArrayDeque<>(List.of(codigos));
		return new EmissaoDeCarteirinha(jdbc, fila::removeFirst, auditoria, propriedades);
	}

}
