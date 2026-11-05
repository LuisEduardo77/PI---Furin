package br.com.furin.access.portaria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import org.springframework.test.web.servlet.MockMvc;

import br.com.furin.access.config.SegurancaConfig;

/**
 * Bloqueio do aluno com pedidos ao mesmo tempo (T-26; RN08): sem a transação do teste,
 * para que cada leitura tenha a sua, como na aplicação. Os dados são gravados de verdade
 * e apagados no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeituraConcorrenteTests {

	private static final String CODIGO = "0f1e2d3c4b5a69788796a5b4c3d2e1f0";
	private static final String FUNCIONARIO = "portaria.concorrencia";
	private static final int PEDIDOS = 2;

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private long usuario;
	private long turma;
	private long aluno;
	private long vinculo;

	@BeforeEach
	void preparar() {
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant ontem = Instant.now().minus(Duration.ofDays(1));
		usuario = cena.funcionario(FUNCIONARIO, ontem);
		turma = cena.turma("9Z-T26");
		aluno = cena.aluno("T26-900", "Carla Teste", true, ontem);
		vinculo = cena.vinculo(aluno, turma, ontem, null);
		cena.carteirinha(aluno, CODIGO, ontem, null, null);
	}

	@AfterEach
	void apagar() {
		jdbc.update("DELETE FROM registro_acesso WHERE id_vinculo = ?", vinculo);
		jdbc.update("DELETE FROM carteirinha WHERE id_aluno = ?", aluno);
		jdbc.update("DELETE FROM vinculo_turma WHERE id_vinculo = ?", vinculo);
		jdbc.update("DELETE FROM aluno WHERE id_aluno = ?", aluno);
		jdbc.update("DELETE FROM turma WHERE id_turma = ?", turma);
		jdbc.update("DELETE FROM usuario WHERE id_usuario = ?", usuario);
	}

	@Test
	void duasLeiturasAoMesmoTempoGeramUmaLiberacaoSo() throws Exception {
		List<MockHttpServletResponse> respostas = aoMesmoTempo(UUID.randomUUID(), UUID.randomUUID());

		// RN04: a que chegar depois recebe a mesma liberação, com 200 no lugar do 201
		assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(201, 200);
		assertThat(respostas.get(0).getContentAsString()).isEqualTo(respostas.get(1).getContentAsString());
		assertThat(registros()).isEqualTo(1);
	}

	@Test
	void doisEnviosDaMesmaChaveAoMesmoTempoDevolvemAMesmaLiberacao() throws Exception {
		UUID chave = UUID.randomUUID();
		List<MockHttpServletResponse> respostas = aoMesmoTempo(chave, chave);

		assertThat(respostas).extracting(MockHttpServletResponse::getStatus).containsOnly(201);
		assertThat(respostas.get(0).getContentAsString()).isEqualTo(respostas.get(1).getContentAsString());
		assertThat(registros()).isEqualTo(1);
	}

	private List<MockHttpServletResponse> aoMesmoTempo(UUID... chaves) throws Exception {
		ExecutorService threads = Executors.newFixedThreadPool(PEDIDOS);
		CountDownLatch largada = new CountDownLatch(1);
		try {
			List<Future<MockHttpServletResponse>> pedidos = Arrays.stream(chaves)
					.map(chave -> threads.submit(leitura(chave, largada))).toList();
			largada.countDown();
			List<MockHttpServletResponse> respostas = new ArrayList<>();
			for (Future<MockHttpServletResponse> pedido : pedidos) {
				respostas.add(pedido.get());
			}
			return respostas;
		} finally {
			threads.shutdownNow();
		}
	}

	private Callable<MockHttpServletResponse> leitura(UUID chave, CountDownLatch largada) {
		return () -> {
			largada.await();
			return mvc.perform(post("/portaria/leituras")
					.with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO)).with(csrf())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"codigo\":\"" + CODIGO + "\",\"chaveRequisicao\":\"" + chave + "\"}"))
					.andReturn().getResponse();
		};
	}

	private int registros() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM registro_acesso WHERE id_vinculo = ?", Integer.class, vinculo);
	}

}
