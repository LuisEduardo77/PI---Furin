package br.com.furin.access.notificacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.aluno.EdicaoDoAluno;
import br.com.furin.access.aluno.EdicaoDoAluno.Alteracao;
import br.com.furin.access.portaria.CenaDaPortaria;
import br.com.furin.access.portaria.Expiracoes;
import br.com.furin.access.portaria.Giros;
import br.com.furin.access.portaria.Giros.GiroRecusado;
import br.com.furin.access.portaria.LeituraRecusada;
import br.com.furin.access.portaria.Leituras;
import br.com.furin.access.portaria.Leituras.Desfecho;
import br.com.furin.access.portaria.Liberacoes;
import br.com.furin.access.portaria.RegistroManual;
import br.com.furin.access.portaria.TipoMovimentacao;

/**
 * Criação da notificação ao contato principal (T-38; RF21, passos 1 a 3; RN11; CA-RF21-02,
 * 03, 05 e 08). Os serviços da portaria são chamados direto, na transação do teste. O
 * relógio começa em 20/10/2026, 07:00 no horário da escola. Só dados fictícios: Ana, Bia e
 * Davi; Carla é o contato de Ana e de Davi.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificacaoTests {

	private static final String FUNCIONARIO = "notificacao.teste";
	private static final String CODIGO_ANA = "d1b2c3d4e5f60718293a4b5c6d7e8f38";
	private static final String CODIGO_BIA = "d2b2c3d4e5f60718293a4b5c6d7e8f38";
	private static final String CODIGO_DAVI = "d3b2c3d4e5f60718293a4b5c6d7e8f38";
	private static final String TELEFONE = "+55 11 900000038";

	/** 20/10/2026, 07:00 no horário da escola. */
	private static final Instant INICIO = LocalDateTime.of(2026, 10, 20, 7, 0)
			.atZone(RelogioAjustavel.FUSO_DA_ESCOLA).toInstant();

	@TestBean(name = "relogio")
	private Clock relogio;

	static Clock relogio() {
		return new RelogioAjustavel(INICIO);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private Leituras leituras;

	@Autowired
	private Giros giros;

	@Autowired
	private Liberacoes liberacoes;

	@Autowired
	private Expiracoes expiracoes;

	@Autowired
	private RegistroManual registroManual;

	@Autowired
	private EdicaoDoAluno edicao;

	@Autowired
	private Notificacoes notificacoes;

	@Autowired
	private MensagemDaNotificacao mensagens;

	@Autowired
	private EnvioDeNotificacoes envio;

	private long turma;
	private long ana;
	private long bia;
	private long davi;
	private long carlaDeAna;
	private long carlaDeDavi;

	@BeforeEach
	void preparar() {
		CenaDaPortaria cena = new CenaDaPortaria(jdbc);
		Instant antes = INICIO.minus(Duration.ofDays(30));
		cena.funcionario(FUNCIONARIO, antes);
		turma = cena.turma("6A-T38");
		ana = aluno(cena, "T38-001", "Ana Teste", CODIGO_ANA, antes);
		bia = aluno(cena, "T38-002", "Bia Teste", CODIGO_BIA, antes);
		davi = aluno(cena, "T38-003", "Davi Teste", CODIGO_DAVI, antes);
		carlaDeAna = contato(ana, "Carla Teste", "carla.teste@example.com", true, true);
		// CA-RF21-02: o contato de Bia está com a opção desligada
		contato(bia, "Bruna Teste", "bruna.teste@example.com", true, false);
		carlaDeDavi = contato(davi, "Carla Teste", "carla.teste@example.com", true, true);
	}

	// --- Criação (RN11, item 1) ---

	@Test
	void entradaConfirmadaDeAnaCriaUmaNotificacaoPendenteParaCarla() {
		long liberacao = ler(CODIGO_ANA);
		avancar(Duration.ofSeconds(3));
		Instant giro = relogio.instant();

		girar(liberacao);

		List<Map<String, Object>> criadas = notificacoesDo(liberacao);
		assertThat(criadas).hasSize(1);
		assertThat(criadas.get(0)).containsEntry("id_contato", carlaDeAna).containsEntry("canal", "EMAIL")
				.containsEntry("status", "PENDENTE").containsEntry("tentativas", 0L)
				.containsEntry("criado_em", CenaDaPortaria.utc(giro)).containsEntry("ultima_tentativa_em", null);
	}

	@Test
	void reenvioDoGiroNaoCriaOutraEOBancoRecusaADuplicada() {
		// RF21, passo 3: UNIQUE(id_registro, id_contato)
		long liberacao = ler(CODIGO_ANA);
		UUID chave = UUID.randomUUID();
		giros.confirmar(liberacao, chave);
		avancar(Duration.ofSeconds(1));
		giros.confirmar(liberacao, chave);

		assertThat(notificacoesDo(liberacao)).hasSize(1);
		Instant agora = relogio.instant();
		assertThatThrownBy(() -> notificacoes.criarParaConfirmada(liberacao, agora))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void contatoQueNaoEhOPrincipalOuEstaInativoNaoRecebe() {
		// RF21, passo 1: só o contato principal ativo
		jdbc.update("UPDATE contato_responsavel SET principal = 0 WHERE id_contato = ?", carlaDeAna);
		contato(ana, "Outro Contato Teste", "outro.teste@example.com", false, true);
		long semPrincipal = ler(CODIGO_ANA);
		girar(semPrincipal);

		avancar(Duration.ofHours(5));
		jdbc.update("UPDATE contato_responsavel SET principal = 1, ativo = 0 WHERE id_contato = ?", carlaDeAna);
		long principalInativo = ler(CODIGO_ANA);
		girar(principalInativo);

		assertThat(notificacoesDo(semPrincipal)).isEmpty();
		assertThat(notificacoesDo(principalInativo)).isEmpty();
	}

	@Test
	void foraDoPrototipoNadaEEnviadoEANotificacaoContinuaPendente() {
		// T-39: o EnvioSimulado só existe no perfil prototipo (DEC-18, DEC-25, DEC-32)
		long liberacao = ler(CODIGO_ANA);
		girar(liberacao);

		envio.enviar(id(notificacoesDo(liberacao).get(0)));

		assertThat(notificacoesDo(liberacao).get(0)).containsEntry("status", "PENDENTE").containsEntry("tentativas", 0L);
	}

	// --- CA-RF21-02 ---

	@Test
	void entradaConfirmadaDeBiaNaoGeraNotificacao() {
		long liberacao = ler(CODIGO_BIA);
		girar(liberacao);

		assertThat(jdbc.queryForObject("SELECT situacao FROM registro_acesso WHERE id_registro = ?", String.class,
				liberacao)).isEqualTo("CONFIRMADA");
		assertThat(notificacoesDo(liberacao)).isEmpty();
	}

	// --- CA-RF21-03 ---

	@Test
	void liberacaoAguardandoGiroNaoGeraNotificacao() {
		long liberacao = ler(CODIGO_ANA);

		assertThat(notificacoesDo(liberacao)).isEmpty();
		// Nem chamando o serviço direto: só a CONFIRMADA serve
		assertThat(notificacoes.criarParaConfirmada(liberacao, relogio.instant())).isZero();
		assertThat(totalDeNotificacoes()).isZero();
	}

	@Test
	void liberacaoCanceladaPeloFuncionarioOuExpiradaNaoGeraNotificacao() {
		long cancelada = ler(CODIGO_ANA);
		liberacoes.cancelar(cancelada);
		UUID chaveDoCancelado = UUID.randomUUID();
		assertThatThrownBy(() -> giros.confirmar(cancelada, chaveDoCancelado)).isInstanceOf(GiroRecusado.class);

		avancar(Duration.ofSeconds(5));
		long expirada = ler(CODIGO_ANA);
		avancar(Duration.ofSeconds(20));
		expiracoes.expirarVencidas();
		UUID chaveDoExpirado = UUID.randomUUID();
		assertThatThrownBy(() -> giros.confirmar(expirada, chaveDoExpirado)).isInstanceOf(GiroRecusado.class);

		assertThat(situacao(cancelada)).isEqualTo("CANCELADA");
		assertThat(situacao(expirada)).isEqualTo("CANCELADA");
		assertThat(notificacoes.criarParaConfirmada(cancelada, relogio.instant())).isZero();
		assertThat(totalDeNotificacoes()).isZero();
	}

	@Test
	void leituraIgnoradaOuRecusadaNaoGeraNotificacao() {
		long liberacao = ler(CODIGO_ANA);
		avancar(Duration.ofSeconds(1));
		// RN03: o mesmo código dentro da janela é ignorado
		assertThat(leituras.ler(CODIGO_ANA, UUID.randomUUID(), FUNCIONARIO).desfecho()).isEqualTo(Desfecho.JA_RECEBIDA);
		liberacoes.cancelar(liberacao);

		// Recusadas: código que não existe e aluno inativo
		jdbc.update("UPDATE aluno SET ativo = 0 WHERE id_aluno = ?", bia);
		UUID chaveDoInexistente = UUID.randomUUID();
		assertThatThrownBy(() -> leituras.ler("ffffffffffffffffffffffffffffff38", chaveDoInexistente, FUNCIONARIO))
				.isInstanceOf(LeituraRecusada.class);
		UUID chaveDoInativo = UUID.randomUUID();
		assertThatThrownBy(() -> leituras.ler(CODIGO_BIA, chaveDoInativo, FUNCIONARIO))
				.isInstanceOf(LeituraRecusada.class);

		assertThat(totalDeNotificacoes()).isZero();
	}

	// --- Registro manual (RN11, item 7; DEC-28, B3) ---

	@Test
	void registroManualGeraNotificacaoComRegistroManualEOHorarioInformado() {
		avancar(Duration.ofMinutes(30));
		Instant informado = relogio.instant().minus(Duration.ofMinutes(25));

		long registro = registroManual.registrar(ana, informado, "Carteirinha esquecida", TipoMovimentacao.ENTRADA,
				UUID.randomUUID(), FUNCIONARIO).idRegistro();

		List<Map<String, Object>> criadas = notificacoesDo(registro);
		assertThat(criadas).hasSize(1);
		assertThat(criadas.get(0)).containsEntry("id_contato", carlaDeAna).containsEntry("status", "PENDENTE");
		MensagemDaNotificacao.Mensagem mensagem = mensagens.montar(id(criadas.get(0))).orElseThrow();
		assertThat(mensagem.corpo()).startsWith("Ana entrou na escola em " + naEscola(informado) + " (registro manual).");
	}

	@Test
	void reenvioDoRegistroManualNaoCriaOutra() {
		avancar(Duration.ofMinutes(30));
		UUID chave = UUID.randomUUID();
		Instant informado = relogio.instant().minus(Duration.ofMinutes(25));
		long registro = registroManual.registrar(ana, informado, "Carteirinha esquecida", TipoMovimentacao.ENTRADA,
				chave, FUNCIONARIO).idRegistro();
		registroManual.registrar(ana, informado, "Carteirinha esquecida", TipoMovimentacao.ENTRADA, chave, FUNCIONARIO);

		assertThat(notificacoesDo(registro)).hasSize(1);
	}

	// --- CA-RF21-05 e conteúdo mínimo (RN11, item 3) ---

	@Test
	void tabelaNotificacaoNaoGuardaOCorpoDaMensagem() {
		List<String> colunas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
				+ "WHERE table_schema = DATABASE() AND table_name = 'notificacao' ORDER BY ordinal_position", String.class);

		assertThat(colunas).containsExactly("id_notificacao", "id_registro", "id_contato", "canal", "status",
				"tentativas", "criado_em", "ultima_tentativa_em");
	}

	@Test
	void mensagemTemSoOPrimeiroNomeOTipoADataAHoraEAForma() {
		// O relógio é do contexto e anda de um teste para outro: os horários esperados vêm dele
		Instant horaDaEntrada = relogio.instant();
		long entrada = ler(CODIGO_ANA);
		girar(entrada);
		avancar(Duration.ofHours(5));
		Instant horaDaSaida = relogio.instant();
		long saida = ler(CODIGO_ANA);
		girar(saida);

		MensagemDaNotificacao.Mensagem daEntrada = mensagens.montar(id(notificacoesDo(entrada).get(0))).orElseThrow();
		MensagemDaNotificacao.Mensagem daSaida = mensagens.montar(id(notificacoesDo(saida).get(0))).orElseThrow();

		assertThat(daEntrada.destinatario()).isEqualTo("carla.teste@example.com");
		assertThat(daEntrada.assunto()).isEqualTo("Furin Access — entrada registrada");
		assertThat(daEntrada.corpo()).isEqualTo("Ana entrou na escola em " + naEscola(horaDaEntrada)
				+ " (leitura da carteirinha).\nEm caso de dúvida, procure a secretaria da escola.\n"
				+ "Para deixar de receber estes avisos, peça à escola.");
		assertThat(daSaida.assunto()).isEqualTo("Furin Access — saída registrada");
		assertThat(daSaida.corpo()).startsWith("Ana saiu da escola em " + naEscola(horaDaSaida)
				+ " (leitura da carteirinha).");
		// DEC-37, A10 §5.34: sem nome completo, turma, matrícula, foto nem link
		for (MensagemDaNotificacao.Mensagem mensagem : List.of(daEntrada, daSaida)) {
			assertThat(mensagem.assunto() + mensagem.corpo()).doesNotContain("Ana Teste", "6A-T38", "T38-001", "http",
					"www", ".png");
		}
		assertThat(mensagens.montar(999_999_999L)).isEmpty();
	}

	// --- CA-RF21-08 ---

	@Test
	void avisoDeDaviDesligadoNaEdicaoNaoGeraNotificacaoEAnaContinua() throws IOException {
		long antesDeDesligar = ler(CODIGO_DAVI);
		girar(antesDeDesligar);
		avancar(Duration.ofMinutes(1));

		// A pedido de Carla, o funcionário desliga o aviso de Davi (RF08, fluxo A5; DEC-38)
		edicao.gravar(davi, new Alteracao("Davi Teste", "T38-003", turma, "6A-T38 — Manhã", false, "Carla Teste",
				TELEFONE, "carla.teste@example.com", false), null);
		assertThat(jdbc.queryForObject("SELECT recebe_notificacao FROM contato_responsavel WHERE id_contato = ?",
				Boolean.class, carlaDeDavi)).isFalse();

		avancar(Duration.ofHours(5));
		long depoisDeDesligar = ler(CODIGO_DAVI);
		girar(depoisDeDesligar);
		avancar(Duration.ofSeconds(5));
		long deAna = ler(CODIGO_ANA);
		girar(deAna);

		assertThat(notificacoesDo(depoisDeDesligar)).isEmpty();
		assertThat(notificacoesDo(deAna)).hasSize(1);
		// RF21, passo 10: a notificação já criada continua como estava
		assertThat(notificacoesDo(antesDeDesligar)).singleElement().satisfies(
				notificacao -> assertThat(notificacao).containsEntry("status", "PENDENTE"));
	}

	// --- Apoio ---

	private long aluno(CenaDaPortaria cena, String matricula, String nome, String codigo, Instant antes) {
		long id = cena.aluno(matricula, nome, true, antes);
		cena.vinculo(id, turma, antes, null);
		cena.carteirinha(id, codigo, antes, null, null);
		return id;
	}

	private long contato(long idAluno, String nome, String email, boolean principal, boolean recebe) {
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, ?, ?, ?, 1, ?, ?)", idAluno, nome, TELEFONE, email, principal, recebe);
		return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
	}

	private long ler(String codigo) {
		return leituras.ler(codigo, UUID.randomUUID(), FUNCIONARIO).liberacao().idLiberacao();
	}

	private void girar(long liberacao) {
		giros.confirmar(liberacao, UUID.randomUUID());
	}

	private List<Map<String, Object>> notificacoesDo(long registro) {
		// Os UNSIGNED voltam como BigInteger: o CAST os deixa como Long
		return jdbc.queryForList("SELECT CAST(id_notificacao AS SIGNED) AS id_notificacao, "
				+ "CAST(id_contato AS SIGNED) AS id_contato, canal, status, CAST(tentativas AS SIGNED) AS tentativas, "
				+ "criado_em, ultima_tentativa_em FROM notificacao WHERE id_registro = ?", registro);
	}

	private int totalDeNotificacoes() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM notificacao n JOIN registro_acesso r "
				+ "ON r.id_registro = n.id_registro JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo "
				+ "WHERE v.id_aluno IN (?, ?, ?)", Integer.class, ana, bia, davi);
	}

	private String situacao(long registro) {
		return jdbc.queryForObject("SELECT situacao FROM registro_acesso WHERE id_registro = ?", String.class, registro);
	}

	/** "20/10/2026 às 07:00", no fuso da escola (RN10). */
	private static String naEscola(Instant instante) {
		return DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(RelogioAjustavel.FUSO_DA_ESCOLA)
				.format(instante);
	}

	private static long id(Map<String, Object> notificacao) {
		return ((Number) notificacao.get("id_notificacao")).longValue();
	}

	private void avancar(Duration tempo) {
		((RelogioAjustavel) relogio).avancar(tempo);
	}

}
