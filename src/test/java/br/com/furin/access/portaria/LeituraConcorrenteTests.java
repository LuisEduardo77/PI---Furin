package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Pedidos ao mesmo tempo (T-26, T-27; RN04, RN05, RN08; CA-RF15-05): sem a transação do
 * teste, para que cada leitura tenha a sua, como na aplicação. O relógio fica parado, e
 * os dados são gravados de verdade e apagados no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeituraConcorrenteTests {

	private static final String CODIGO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
	private static final String CODIGO_BIA = "1f1e2d3c4b5a69788796a5b4c3d2e1f0";
	private static final String FUNCIONARIO = "portaria.concorrencia";

	/** 20/10/2026, 07:00 no horário da escola. */
	private static final Instant INICIO = LocalDateTime.of(2026, 10, 20, 7, 0)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(INICIO);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private TransactionTemplate transacao;

	private CenaDaPortaria cena;
	private long usuario;
	private long turma;
	private long aluno;
	private long vinculo;
	private long bia;
	private long vinculoBia;
	private long carteirinhaBia;

	@BeforeEach
	void preparar() {
		cena = new CenaDaPortaria(jdbc);
		Instant ontem = INICIO.minus(Duration.ofDays(1));
		usuario = cena.funcionario(FUNCIONARIO, ontem);
		turma = cena.turma("9Z-T26");
		aluno = cena.aluno("T26-900", "Carla Teste", true, ontem);
		vinculo = cena.vinculo(aluno, turma, ontem, null);
		cena.carteirinha(aluno, CODIGO, ontem, null, null);
		bia = cena.aluno("T27-901", "Bia Teste", true, ontem);
		vinculoBia = cena.vinculo(bia, turma, ontem, null);
		carteirinhaBia = cena.carteirinha(bia, CODIGO_BIA, ontem, null, null);
	}

	@AfterEach
	void apagar() {
		// CA-RF15-07, com os dados gravados de verdade
		assertThat(cena.confirmadasRepetidas()).isZero();
		jdbc.update("DELETE FROM registro_acesso WHERE id_vinculo IN (?, ?)", vinculo, vinculoBia);
		jdbc.update("DELETE FROM carteirinha WHERE id_aluno IN (?, ?)", aluno, bia);
		jdbc.update("DELETE FROM vinculo_turma WHERE id_vinculo IN (?, ?)", vinculo, vinculoBia);
		jdbc.update("DELETE FROM aluno WHERE id_aluno IN (?, ?)", aluno, bia);
		jdbc.update("DELETE FROM turma WHERE id_turma = ?", turma);
		jdbc.update("DELETE FROM usuario WHERE id_usuario = ?", usuario);
	}

	@Test
	void duasLeiturasAoMesmoTempoGeramUmaLiberacaoSo() throws Exception {
		// CA-RF15-05: chaves diferentes; o bloqueio do aluno faz a segunda esperar a primeira
		List<MockHttpServletResponse> respostas = aoMesmoTempo(leitura(CODIGO, UUID.randomUUID()),
				leitura(CODIGO, UUID.randomUUID()));

		// A que chegar depois está na janela de 2 s (RN03): 202, com a mesma liberação
		assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 202);
		assertThat(respostas.get(0).getContentAsString()).isEqualTo(respostas.get(1).getContentAsString());
		assertThat(registros(vinculo)).isEqualTo(1);
	}

	@Test
	void doisEnviosDaMesmaChaveAoMesmoTempoDevolvemAMesmaLiberacao() throws Exception {
		UUID chave = UUID.randomUUID();
		List<MockHttpServletResponse> respostas = aoMesmoTempo(leitura(CODIGO, chave), leitura(CODIGO, chave));

		assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
		assertThat(respostas.get(0).getContentAsString()).isEqualTo(respostas.get(1).getContentAsString());
		assertThat(registros(vinculo)).isEqualTo(1);
	}

	@Test
	void leiturasDeDoisAlunosAoMesmoTempoLiberamUmSo() throws Exception {
		// RN05; DEC-46: cada leitura bloqueia o seu aluno, então só o banco serializa as duas
		List<MockHttpServletResponse> respostas = aoMesmoTempo(leitura(CODIGO, UUID.randomUUID()),
				leitura(CODIGO_BIA, UUID.randomUUID()));

		assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 422);
		assertThat(respostas).filteredOn(resposta -> resposta.getStatus() == 422).singleElement()
				.extracting(this::corpo).asString().contains("OUTRA_LIBERACAO_PENDENTE");
		assertThat(registros(vinculo) + registros(vinculoBia)).isEqualTo(1);
	}

	@Test
	void liberacaoDeOutroAlunoGravadaDuranteALeituraFazARecusa() throws Exception {
		// DEC-46: a pendente da Bia ainda não foi confirmada quando a leitura confere a RN05;
		// a gravação espera a restrição única e, com o commit da Bia, vira a recusa
		CountDownLatch gravada = new CountDownLatch(1);
		ExecutorService outra = Executors.newSingleThreadExecutor();
		try {
			Future<?> transacaoDaBia = outra.submit(() -> transacao.executeWithoutResult(estado -> {
				cena.pendente(vinculoBia, usuario, carteirinhaBia, TipoMovimentacao.ENTRADA, INICIO);
				gravada.countDown();
				esperar(Duration.ofMillis(700));
			}));
			assertThat(gravada.await(10, TimeUnit.SECONDS)).isTrue();

			MockHttpServletResponse resposta = leitura(CODIGO, UUID.randomUUID()).call();
			transacaoDaBia.get(10, TimeUnit.SECONDS);

			assertThat(resposta.getStatus()).isEqualTo(422);
			assertThat(resposta.getContentAsString()).contains("OUTRA_LIBERACAO_PENDENTE");
			assertThat(registros(vinculo)).isZero();
			assertThat(registros(vinculoBia)).isEqualTo(1);
		} finally {
			outra.shutdownNow();
		}
	}

	@SafeVarargs
	private static List<MockHttpServletResponse> aoMesmoTempo(Callable<MockHttpServletResponse>... leituras)
			throws Exception {
		ExecutorService threads = Executors.newFixedThreadPool(leituras.length);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> pedidos = new ArrayList<>();
			for (Callable<MockHttpServletResponse> leitura : leituras) {
				pedidos.add(threads.submit(() -> {
					largada.await();
					return leitura.call();
				}));
			}
			largada.countDown();
			List<MockHttpServletResponse> respostas = new ArrayList<>();
			for (Future<MockHttpServletResponse> pedido : pedidos) {
				respostas.add(pedido.get(30, TimeUnit.SECONDS));
			}
			return respostas;
		} finally {
			threads.shutdownNow();
		}
	}

	private Callable<MockHttpServletResponse> leitura(String codigo, UUID chave) {
		return () -> mvc.perform(post("/portaria/leituras")
				.with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO)).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"chaveRequisicao\":\"" + chave + "\"}"))
				.andReturn().getResponse();
	}

	private String corpo(MockHttpServletResponse resposta) {
		try {
			return resposta.getContentAsString();
		} catch (UnsupportedEncodingException erro) {
			throw new IllegalStateException(erro);
		}
	}

	private static void esperar(Duration tempo) {
		try {
			Thread.sleep(tempo);
		} catch (InterruptedException erro) {
			Thread.currentThread().interrupt();
		}
	}

	private int registros(long idVinculo) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ?", Integer.class,
				idVinculo);
	}

}
