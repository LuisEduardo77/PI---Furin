package br.com.furin.access.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.aluno.LimpezaDeAlunos;
import br.com.furin.access.config.SegurancaConfig;
import br.com.furin.access.notificacao.MensagemDaNotificacao.Mensagem;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.Giros;
import br.com.furin.access.portaria.Leituras;

/**
 * Envio das notificações depois do commit (T-39; RF21, passos 4 a 6 e 8; RN11, itens 2 e 6;
 * CA-RF21-01, 04, 06, 07, 09 e 10), no perfil {@code prototipo}, com a carga V2 desligada.
 * <p>
 * Sem a transação do teste: o envio só começa depois do commit, em outra thread, e só
 * enxerga dados gravados de verdade. A cena é gravada antes de cada teste e apagada depois.
 * O {@link EnvioSimulado} é trocado por {@link EnvioControlado}, que se comporta igual, mas
 * pode falhar ou esperar quando o teste manda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "test", "prototipo" })
@TestPropertySource(properties = "furin.carga.ativa=false")
@ExtendWith(OutputCaptureExtension.class)
class EnvioDeNotificacoesTests {

	private static final String FUNCIONARIO = "envio.teste";
	private static final String PREFIXO = "T39-";
	private static final String TURMA = "6A-T39";
	private static final String CODIGO_ANA = "e1b2c3d4e5f60718293a4b5c6d7e8f39";
	private static final String CODIGO_BIA = "e2b2c3d4e5f60718293a4b5c6d7e8f39";
	private static final String EMAIL_CARLA = "carla.teste@example.com";
	private static final Duration ESPERA_MAXIMA = Duration.ofSeconds(10);

	/** 20/10/2026, 07:02 no horário da escola. */
	private static final Instant INICIO = LocalDateTime.of(2026, 10, 20, 7, 2)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(INICIO);
	}

	@TestBean(name = "envioSimulado", methodName = "envioControlado")
	private EnvioSimulado envio;

	static EnvioSimulado envioControlado() {
		return new EnvioControlado();
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Leituras leituras;

	@Autowired
	private Giros giros;

	@Autowired
	private TransactionTemplate transacao;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mapeamentos;

	@BeforeEach
	void preparar() {
		apagarACena();
		controlado().reiniciar();
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant antes = relogio.instant().minus(Duration.ofDays(30));
		cena.funcionario(FUNCIONARIO, antes);
		long turma = cena.turma(TURMA);
		long ana = aluno(cena, turma, PREFIXO + "001", "Ana Teste", CODIGO_ANA, antes);
		long bia = aluno(cena, turma, PREFIXO + "002", "Bia Teste", CODIGO_BIA, antes);
		contato(ana, "Carla Teste", EMAIL_CARLA, true);
		// CA-RF21-02: o contato de Bia não recebe
		contato(bia, "Bruna Teste", "bruna.teste@example.com", false);
	}

	@AfterEach
	void apagarACena() {
		controlado().liberar();
		// Espera o envio em curso terminar antes de apagar o que ele usa
		esperarAte(() -> controlado().emCurso.get() == 0);
		LimpezaDeAlunos.apagar(jdbc, "matricula LIKE ?", PREFIXO + "%");
		jdbc.update("DELETE FROM turma WHERE nome = ?", TURMA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
	}

	// --- CA-RF21-01 e 04: entrada de Ana, notificação SIMULADA e aviso na tela ---

	@Test
	void entradaDeAnaGeraUmaNotificacaoSimuladaEOGiroTrazOAviso() throws Exception {
		long liberacao = ler(CODIGO_ANA);
		// O relógio é do contexto e anda de um teste para outro: o horário esperado vem dele
		String horario = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
				.withZone(RelogioAjustavel.FUSO_DA_ESCOLA).format(relogio.instant());

		mvc.perform(post("/portaria/giros").with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO))
				.with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idLiberacao\":" + liberacao + ",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avisoAoResponsavel.titulo").value("Simulação — nenhum e-mail foi enviado"))
				.andExpect(jsonPath("$.avisoAoResponsavel.destinatario").value("c***@example.com"))
				.andExpect(jsonPath("$.avisoAoResponsavel.assunto").value("Furin Access — entrada registrada"))
				.andExpect(jsonPath("$.avisoAoResponsavel.corpo").value(Matchers.startsWith(
						"Ana entrou na escola em " + horario + " (leitura da carteirinha).")))
				// CA-RF21-04: sem link, nome completo, turma, matrícula nem foto
				.andExpect(jsonPath("$.avisoAoResponsavel.corpo").value(Matchers.not(Matchers.anyOf(
						Matchers.containsString("http"), Matchers.containsString("www"),
						Matchers.containsString("Ana Teste"), Matchers.containsString(TURMA),
						Matchers.containsString(PREFIXO), Matchers.containsString(".png")))));

		esperarAte(() -> "SIMULADA".equals(notificacao(liberacao).get("status")));
		List<Map<String, Object>> criadas = notificacoes(liberacao);
		assertThat(criadas).hasSize(1);
		assertThat(criadas.get(0)).containsEntry("status", "SIMULADA").containsEntry("tentativas", 1L);
		assertThat(criadas.get(0).get("ultima_tentativa_em")).isNotNull();
		// O e-mail que "sairia" é o mesmo da tela, e nada mais foi pedido ao serviço
		assertThat(controlado().chamadas.get()).isEqualTo(1);
		assertThat(controlado().ultima.get().destinatario()).isEqualTo(EMAIL_CARLA);
	}

	@Test
	void entradaDeBiaNaoTemAvisoNemEnvio() throws Exception {
		long liberacao = ler(CODIGO_BIA);

		mvc.perform(post("/portaria/giros").with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO))
				.with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"idLiberacao\":" + liberacao + ",\"chaveRequisicao\":\"" + UUID.randomUUID() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.avisoAoResponsavel").value(Matchers.nullValue()));

		assertThat(notificacoes(liberacao)).isEmpty();
		assertThat(controlado().chamadas.get()).isZero();
	}

	@Test
	void registroManualMostraOAvisoComRegistroManualEEnviaSimulado() throws Exception {
		// RN11, item 7 (DEC-28, B3): o texto diz "registro manual" e o horário informado
		((RelogioAjustavel) relogio).avancar(Duration.ofMinutes(30));
		Instant informado = relogio.instant().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.MINUTES);
		DateTimeFormatter campo = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
				.withZone(RelogioAjustavel.FUSO_DA_ESCOLA);
		String horario = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
				.withZone(RelogioAjustavel.FUSO_DA_ESCOLA).format(informado);
		long ana = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class, PREFIXO + "001");

		AvisoDaSimulacao.Aviso aviso = (AvisoDaSimulacao.Aviso) mvc.perform(post("/portaria/manual/" + ana)
				.with(user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO)).with(csrf())
				.param("ocorridoEm", campo.format(informado)).param("motivo", "Carteirinha esquecida")
				.param("tipoMostrado", "ENTRADA").param("chaveRequisicao", UUID.randomUUID().toString()))
				.andExpect(status().is3xxRedirection())
				.andReturn().getFlashMap().get("avisoAoResponsavel");

		assertThat(aviso).isNotNull();
		assertThat(aviso.titulo()).isEqualTo("Simulação — nenhum e-mail foi enviado");
		assertThat(aviso.destinatario()).isEqualTo("c***@example.com");
		assertThat(aviso.corpo()).startsWith("Ana entrou na escola em " + horario + " (registro manual).");

		long registro = jdbc.queryForObject("SELECT r.id_registro FROM registro_acesso r JOIN vinculo_turma v "
				+ "ON v.id_vinculo = r.id_vinculo WHERE v.id_aluno = ? AND r.forma = 'MANUAL'", Long.class, ana);
		esperarAte(() -> "SIMULADA".equals(notificacao(registro).get("status")));
	}

	// --- CA-RF21-06: falha no envio ---

	@Test
	void falhaEmTodasAsTentativasDeixaFalhaComTresTentativasEAMovimentacaoConfirmada(CapturedOutput saida) {
		controlado().falhar(Integer.MAX_VALUE);
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao);
		Object ocorrido = registro(liberacao).get("ocorrido_em");

		esperarAte(() -> Long.valueOf(3).equals(notificacao(liberacao).get("tentativas")));
		esperarAte(() -> controlado().emCurso.get() == 0);

		assertThat(notificacao(liberacao)).containsEntry("status", "FALHA").containsEntry("tentativas", 3L);
		assertThat(controlado().chamadas.get()).as("furin.notificacao.max-tentativas = 3").isEqualTo(3);
		assertThat(registro(liberacao)).containsEntry("situacao", "CONFIRMADA").containsEntry("ocorrido_em", ocorrido);
		long idNotificacao = id(notificacao(liberacao));
		assertThat(linhasDoLog(saida, "evento=NOTIFICACAO_FALHA", idNotificacao)).hasSize(3)
				.allSatisfy(linha -> assertThat(linha).contains("destinatario=c***@example.com")
						.doesNotContain(EMAIL_CARLA, "entrou na escola"));
	}

	@Test
	void falhaQuePassaNaTerceiraTentativaTerminaSimulada() {
		controlado().falhar(2);
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao);

		esperarAte(() -> "SIMULADA".equals(notificacao(liberacao).get("status")));

		assertThat(notificacao(liberacao)).containsEntry("tentativas", 3L);
		assertThat(registro(liberacao)).containsEntry("situacao", "CONFIRMADA");
	}

	// --- CA-RF21-07: o giro não espera o envio, que só começa depois do commit ---

	@Test
	void giroRespondeSemEsperarOEnvioQueCorreEmOutraThread() {
		controlado().segurar();
		long liberacao = ler(CODIGO_ANA);

		girar(liberacao);

		// O giro voltou com o envio ainda parado: a movimentação já está gravada e a notificação espera
		assertThat(controlado().esperarComecar()).as("o envio começou depois do commit").isTrue();
		assertThat(registro(liberacao)).containsEntry("situacao", "CONFIRMADA");
		assertThat(notificacao(liberacao)).containsEntry("status", "PENDENTE");
		assertThat(controlado().thread.get()).isNotEqualTo(Thread.currentThread().getName());

		controlado().liberar();
		esperarAte(() -> "SIMULADA".equals(notificacao(liberacao).get("status")));
	}

	@Test
	void confirmacaoDesfeitaNaoEnviaNada() throws InterruptedException {
		long liberacao = ler(CODIGO_ANA);

		transacao.executeWithoutResult(status -> {
			giros.confirmar(liberacao, UUID.randomUUID());
			status.setRollbackOnly();
		});
		Thread.sleep(300);

		assertThat(notificacoes(liberacao)).isEmpty();
		assertThat(registro(liberacao)).containsEntry("situacao", "AGUARDANDO_GIRO");
		assertThat(controlado().chamadas.get()).isZero();
	}

	// --- CA-RF21-09: nenhuma rota para os pais/responsáveis ---

	@Test
	void naoExisteRotaParaOsResponsaveis() {
		// A T-17 (AutorizacaoDeTodasAsRotasTests) já prova que toda rota, menos /login, exige um
		// funcionário. Aqui: nenhuma rota é dos responsáveis, e o e-mail não aponta para o sistema
		List<String> caminhos = mapeamentos.getHandlerMethods().keySet().stream()
				.flatMap(info -> info.getPatternValues().stream()).toList();

		Pattern telaDeResponsavel = Pattern.compile("responsav|pais|familia|consulta|verifica|notifica",
				Pattern.CASE_INSENSITIVE);
		assertThat(caminhos).isNotEmpty().noneMatch(caminho -> telaDeResponsavel.matcher(caminho).find());
	}

	// --- CA-RF21-10: log com o e-mail mascarado e sem o corpo ---

	@Test
	void logDoEnvioSimuladoTemOEmailMascaradoESemOCorpo(CapturedOutput saida) {
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao);
		esperarAte(() -> "SIMULADA".equals(notificacao(liberacao).get("status")));

		List<String> linhas = linhasDoLog(saida, "evento=NOTIFICACAO_SIMULADA", id(notificacao(liberacao)));
		assertThat(linhas).singleElement().satisfies(linha -> assertThat(linha)
				.contains("destinatario=c***@example.com")
				.doesNotContain(EMAIL_CARLA, "entrou na escola", "Furin Access —", "Ana"));
	}

	// --- Apoio ---

	/** O {@link EnvioSimulado} de verdade, com falhas e espera controladas pelo teste. */
	static class EnvioControlado extends EnvioSimulado {

		final AtomicInteger chamadas = new AtomicInteger();
		final AtomicInteger emCurso = new AtomicInteger();
		final AtomicInteger falhasRestantes = new AtomicInteger();
		final AtomicReference<Mensagem> ultima = new AtomicReference<>();
		final AtomicReference<String> thread = new AtomicReference<>();
		private volatile CountDownLatch comecou = new CountDownLatch(1);
		private volatile CountDownLatch liberado = new CountDownLatch(0);

		@Override
		public Resultado enviar(Mensagem mensagem) {
			emCurso.incrementAndGet();
			try {
				chamadas.incrementAndGet();
				ultima.set(mensagem);
				thread.set(Thread.currentThread().getName());
				comecou.countDown();
				liberado.await(ESPERA_MAXIMA.toSeconds(), TimeUnit.SECONDS);
				if (falhasRestantes.getAndDecrement() > 0) {
					throw new IllegalStateException("falha simulada no teste");
				}
				return super.enviar(mensagem);
			} catch (InterruptedException interrompido) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(interrompido);
			} finally {
				emCurso.decrementAndGet();
			}
		}

		void reiniciar() {
			chamadas.set(0);
			falhasRestantes.set(0);
			ultima.set(null);
			thread.set(null);
			comecou = new CountDownLatch(1);
			liberado = new CountDownLatch(0);
		}

		void falhar(int vezes) {
			falhasRestantes.set(vezes);
		}

		void segurar() {
			liberado = new CountDownLatch(1);
		}

		void liberar() {
			liberado.countDown();
		}

		boolean esperarComecar() {
			try {
				return comecou.await(ESPERA_MAXIMA.toSeconds(), TimeUnit.SECONDS);
			} catch (InterruptedException interrompido) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
	}

	private EnvioControlado controlado() {
		return (EnvioControlado) envio;
	}

	private long aluno(CenaDaPortaria cena, long turma, String matricula, String nome, String codigo, Instant antes) {
		long id = cena.aluno(matricula, nome, true, antes);
		cena.vinculo(id, turma, antes, null);
		cena.carteirinha(id, codigo, antes, null, null);
		return id;
	}

	private void contato(long aluno, String nome, String email, boolean recebe) {
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, ?, '+55 11 900000039', ?, 1, 1, ?)", aluno, nome, email, recebe);
	}

	private long ler(String codigo) {
		// O relógio é do contexto: anda a cada teste, para nenhuma leitura cair na janela de outra
		((RelogioAjustavel) relogio).avancar(Duration.ofMinutes(1));
		return leituras.ler(codigo, UUID.randomUUID(), FUNCIONARIO).liberacao().idLiberacao();
	}

	private void girar(long liberacao) {
		giros.confirmar(liberacao, UUID.randomUUID());
	}

	private Map<String, Object> registro(long id) {
		return jdbc.queryForMap("SELECT situacao, ocorrido_em FROM registro_acesso WHERE id_registro = ?", id);
	}

	private List<Map<String, Object>> notificacoes(long registro) {
		return jdbc.queryForList("SELECT CAST(id_notificacao AS SIGNED) AS id_notificacao, status, "
				+ "CAST(tentativas AS SIGNED) AS tentativas, ultima_tentativa_em FROM notificacao WHERE id_registro = ?",
				registro);
	}

	private Map<String, Object> notificacao(long registro) {
		List<Map<String, Object>> lista = notificacoes(registro);
		return lista.isEmpty() ? Map.of() : lista.get(0);
	}

	private static long id(Map<String, Object> notificacao) {
		return ((Number) notificacao.get("id_notificacao")).longValue();
	}

	private static List<String> linhasDoLog(CapturedOutput saida, String evento, long idNotificacao) {
		return saida.getOut().lines().filter(linha -> linha.contains(evento))
				.filter(linha -> linha.contains("notificacao=" + idNotificacao + " ")
						|| linha.endsWith("notificacao=" + idNotificacao))
				.toList();
	}

	private static void esperarAte(BooleanSupplier condicao) {
		long limite = System.nanoTime() + ESPERA_MAXIMA.toNanos();
		while (!condicao.getAsBoolean()) {
			if (System.nanoTime() > limite) {
				throw new AssertionError("A condição não aconteceu em " + ESPERA_MAXIMA.toSeconds() + " s");
			}
			try {
				Thread.sleep(20);
			} catch (InterruptedException interrompido) {
				Thread.currentThread().interrupt();
				throw new AssertionError(interrompido);
			}
		}
	}

}
