package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Leitura inválida no servidor (T-31; RF23; RN09; CA-RF23-03, 04 e 07), com um cenário por
 * motivo: a resposta traz o motivo técnico, nada é gravado em {@code registro_acesso},
 * nenhuma notificação é criada e o log recebe {@code LEITURA_RECUSADA} com o motivo e só
 * os 4 primeiros caracteres do código. Os textos da tela estão em
 * {@link PortariaTelaTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class LeituraInvalidaTests {

	private static final String CODIGO_ANA = "a7b2c3d4e5f60718293a4b5c6d7e8f31";
	private static final String CODIGO_REVOGADO = "b7b2c3d4e5f60718293a4b5c6d7e8f31";
	private static final String FUNCIONARIO = "invalida.teste";

	/** 20/10/2026, 12:30 no horário da escola. */
	private static final Instant AGORA = LocalDateTime.of(2026, 10, 20, 12, 30)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(AGORA);
	}

	@MockitoSpyBean
	private RegistrosDaPortaria registros;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private long usuario;
	private long ana;
	private long vinculoAna;
	private long carteirinhaAna;

	@BeforeEach
	void preparar() {
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant ontem = AGORA.minus(Duration.ofDays(1));
		usuario = cena.funcionario(FUNCIONARIO, ontem);
		long turma = cena.turma("9A-T31");
		ana = cena.aluno("T31-001", "Ana Teste", true, ontem);
		vinculoAna = cena.vinculo(ana, turma, ontem.minus(Duration.ofDays(30)), null);
		carteirinhaAna = cena.carteirinha(ana, CODIGO_ANA, ontem, null, null);
		cena.carteirinha(ana, CODIGO_REVOGADO, ontem.minus(Duration.ofDays(10)), null, ontem);
		clearInvocations(registros);
	}

	/** Um cenário por motivo do RF23, passo 1, com o código lido e o motivo esperado. */
	enum Cenario {
		FORA_DO_FORMATO("abc", MotivoDaRecusa.LEITURA_NAO_RECONHECIDA),
		CODIGO_DESCONHECIDO("ffffffffffffffffffffffffffffff31", MotivoDaRecusa.CODIGO_NAO_RECONHECIDO),
		REVOGADA(CODIGO_REVOGADO, MotivoDaRecusa.CARTEIRINHA_REVOGADA),
		EXPIRADA(CODIGO_ANA, MotivoDaRecusa.CARTEIRINHA_EXPIRADA),
		INATIVO(CODIGO_ANA, MotivoDaRecusa.ALUNO_INATIVO),
		SEM_TURMA(CODIGO_ANA, MotivoDaRecusa.SEM_TURMA_VIGENTE),
		SAIDA_PENDENTE(CODIGO_ANA, MotivoDaRecusa.SAIDA_PENDENTE);

		final String codigo;
		final MotivoDaRecusa motivo;

		Cenario(String codigo, MotivoDaRecusa motivo) {
			this.codigo = codigo;
			this.motivo = motivo;
		}
	}

	@ParameterizedTest
	@EnumSource(Cenario.class)
	void leituraRecusadaNaoGravaNadaENaoNotifica(Cenario cenario, CapturedOutput saida) throws Exception {
		preparar(cenario);
		int registrosAntes = registrosDeAcesso();
		int notificacoesAntes = notificacoes();

		ler(cenario.codigo).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value(cenario.motivo.name()));

		// CA-RF23-03
		assertThat(registrosDeAcesso()).isEqualTo(registrosAntes);
		assertThat(notificacoes()).isEqualTo(notificacoesAntes);
		// CA-RF23-07: o motivo e só os 4 primeiros caracteres do código
		String inicio = cenario.codigo.substring(0, Math.min(4, cenario.codigo.length()));
		String linha = saida.getOut().lines().filter(texto -> texto.contains("evento=LEITURA_RECUSADA"))
				.reduce((primeira, outra) -> primeira + "\n" + outra).orElse("");
		assertThat(linha).doesNotContain("\n").contains("motivo=" + cenario.motivo.name(), "codigo=" + inicio + "***");
		if (cenario.codigo.length() > 4) {
			assertThat(linha).doesNotContain(cenario.codigo);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "abc", "a1b2c3d4e5f60718293a4b5c6d7e8f9200112233", "XYZ", "a7b2c3d4-e5f6" })
	void conteudoForaDoFormatoEhRecusadoSemConsultarACarteirinhaNemOAluno(String conteudo) throws Exception {
		// CA-RF23-04: "abc", 40 caracteres e outros
		ler(conteudo).andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.motivo").value("LEITURA_NAO_RECONHECIDA"));

		verify(registros, never()).carteirinhaPorCodigo(anyString());
		verify(registros, never()).bloquearAluno(anyLong());
		verify(registros, never()).vinculoVigente(anyLong(), any());
	}

	@Test
	void codigoNoFormatoConsultaACarteirinha() throws Exception {
		// O contraponto do teste acima: o espião enxerga a consulta quando ela acontece
		ler(CODIGO_ANA).andExpect(status().isCreated());

		verify(registros).carteirinhaPorCodigo(CODIGO_ANA);
	}

	// --- Apoio ---

	private void preparar(Cenario cenario) {
		switch (cenario) {
			case EXPIRADA -> jdbc.update("UPDATE carteirinha SET expira_em = ? WHERE id_carteirinha = ?",
					CenaDaPortaria.utc(AGORA.minusSeconds(1)), carteirinhaAna);
			case INATIVO -> jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", ana);
			case SEM_TURMA -> jdbc.update("UPDATE vinculo_turma SET fim_em = ? WHERE id_vinculo = ?",
					CenaDaPortaria.utc(AGORA.minusSeconds(1)), vinculoAna);
			case SAIDA_PENDENTE -> new CenaDaPortaria(jdbc).confirmada(vinculoAna, usuario, carteirinhaAna,
					TipoMovimentacao.ENTRADA, AGORA.minus(Duration.ofDays(1)));
			default -> {
				// O código já leva à recusa
			}
		}
	}

	private ResultActions ler(String codigo) throws Exception {
		return mvc.perform(post("/portaria/leituras")
				.with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"));
	}

	private int registrosDeAcesso() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso", Integer.class);
	}

	private int notificacoes() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notificacao", Integer.class);
	}

}
