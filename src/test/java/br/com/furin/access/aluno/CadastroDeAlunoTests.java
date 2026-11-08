package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import br.com.furin.access.RelogioAjustavel;
import br.com.furin.access.aluno.CadastroDeAlunos.BlocosGravados;
import br.com.furin.access.aluno.CadastroDeAlunos.NovoAluno;
import br.com.furin.access.aluno.CadastroDeAlunos.TermoConferido;
import br.com.furin.access.carteirinha.EmissaoDeCarteirinha;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Cadastro de aluno (T-19, T-20, T-22; RF04; RF05; RN16): os cinco blocos numa transação.
 * Sem a transação do teste, para o que a transação do cadastro grava ou desfaz ser conferido
 * de verdade. A cena e a área privada são apagadas depois de cada teste. Só dados
 * fictícios (A10 §8.33).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class CadastroDeAlunoTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0 };
	private static final byte[] PDF = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);

	private static final String TURMA = "6A-T19";
	private static final String TURMA_INATIVA = "9Z-T19";
	private static final String MATRICULA_NOVA = "T19-000123";
	private static final String MATRICULA_EXISTENTE = "T19-000999";
	private static final String FUNCIONARIO = "cadastro.teste";

	private static final TermoConferido TERMO_DIGITALIZADO = new TermoConferido("v1-2026",
			LocalDate.now().minusDays(1), null);

	private static final Path AREA_PRIVADA = Path.of(System.getProperty("java.io.tmpdir"),
			"furin-alunos-teste-" + UUID.randomUUID());

	@DynamicPropertySource
	static void areaPrivada(DynamicPropertyRegistry propriedades) {
		propriedades.add("furin.arquivos.diretorio", AREA_PRIVADA::toString);
	}

	@AfterAll
	static void apagarAAreaPrivada() throws IOException {
		FileSystemUtils.deleteRecursively(AREA_PRIVADA);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private CadastroDeAlunos cadastro;

	@Autowired
	private TransactionTemplate transacao;

	@MockitoSpyBean
	private EmissaoDeCarteirinha emissao;

	private long idTurma;

	private long idTurmaInativa;

	@BeforeEach
	void preparar() throws IOException {
		apagarACena();
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'MANHA', 1)", TURMA);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, 2026, 'TARDE', 0)", TURMA_INATIVA);
		idTurma = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = ?", Long.class, TURMA);
		idTurmaInativa = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = ?", Long.class, TURMA_INATIVA);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, 'Bia Exemplo', "
				+ "'sem-arquivo.png', 1, UTC_TIMESTAMP(6))", MATRICULA_EXISTENTE);
		// O conferente do termo é o funcionário da sessão (CA-RF05-04)
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', ?, '{noop}nao-usada', ?, 1, 0, "
				+ "NULL, UTC_TIMESTAMP(6))", FUNCIONARIO, SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	@AfterEach
	void apagarACena() throws IOException {
		LimpezaDeAlunos.apagar(jdbc, "matricula LIKE 'T19-%'");
		jdbc.update("DELETE FROM turma WHERE nome IN (?, ?)", TURMA, TURMA_INATIVA);
		jdbc.update("DELETE FROM usuario WHERE login = ?", FUNCIONARIO);
		FileSystemUtils.deleteRecursively(AREA_PRIVADA);
	}

	// --- Formulário ---

	@Test
	void formularioTemOsCamposDoRf04ESoAsTurmasAtivas() throws Exception {
		mvc.perform(get("/alunos/novo").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<title>Novo aluno | Furin Access</title>")))
				.andExpect(content().string(containsString("Nome do aluno (obrigatório)")))
				.andExpect(content().string(containsString("Matrícula (obrigatório)")))
				.andExpect(content().string(containsString("Turma (obrigatório)")))
				.andExpect(content().string(containsString("Foto do aluno (obrigatório)")))
				.andExpect(content().string(containsString("Nome do responsável (obrigatório)")))
				.andExpect(content().string(containsString("Telefone com DDD (obrigatório)")))
				.andExpect(content().string(containsString("E-mail (obrigatório)")))
				.andExpect(content().string(containsString("JPEG ou PNG, até 2 MB")))
				.andExpect(content().string(containsString(TURMA + " — Manhã")))
				.andExpect(content().string(not(containsString(TURMA_INATIVA))));
	}

	@Test
	void formularioNaoTemCamposDeDadosNaoColetados() throws Exception {
		// CA-RF04-07; CA-RNF05-02
		String pagina = mvc.perform(get("/alunos/novo").with(funcionario()))
				.andReturn().getResponse().getContentAsString().toLowerCase();

		assertThat(pagina).doesNotContain("cpf", "endereço", "nascimento", "biometria", "religião", "saúde");
	}

	@Test
	void inicioDeAlunosLevaAoNovoAluno() throws Exception {
		mvc.perform(get("/alunos").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/alunos/novo\"")));
	}

	// --- RN16: os cinco blocos numa transação ---

	@Test
	void cadastroValidoSalvaOsCincoBlocosEVaiParaAImpressao() throws Exception {
		// CA-RF04-01, CA-RF04-05; RN16: aluno, vínculo vigente, contato principal, consentimento e carteirinha
		String destino = enviar(valido().file(foto(PNG)))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();

		long aluno = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ? AND ativo = 1", Long.class,
				MATRICULA_NOVA);
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM vinculo_turma WHERE id_aluno = ? AND id_turma = ? "
				+ "AND fim_em IS NULL", Integer.class, aluno, idTurma)).isEqualTo(1);
		assertThat(jdbc.queryForMap("SELECT principal, recebe_notificacao, telefone FROM contato_responsavel "
				+ "WHERE id_aluno = ?", aluno))
				.containsEntry("principal", true).containsEntry("recebe_notificacao", true)
				.containsEntry("telefone", "+55 11 900000001");
		assertThat(consentimentos()).isEqualTo(1);
		String codigo = jdbc.queryForObject("SELECT codigo FROM carteirinha WHERE id_aluno = ? AND revogada_em IS NULL",
				String.class, aluno);
		assertThat(codigo).matches("[0-9a-f]{32}");
		long carteirinha = jdbc.queryForObject("SELECT id_carteirinha FROM carteirinha WHERE id_aluno = ?", Long.class,
				aluno);
		// RF04, passo 6: a tela vai para a impressão da carteirinha
		assertThat(destino).isEqualTo("/carteirinhas/" + carteirinha + "/imprimir");
		// Só a foto: a referência do termo é o protocolo
		assertThat(arquivosNaAreaPrivada()).isEqualTo(1);
	}

	@Test
	void falhaNaCarteirinhaDesfazOCadastroInteiro() throws Exception {
		// CA-RF04-02; RN16: a carteirinha é o último bloco. Nada fica, nem a foto
		doThrow(new DataAccessResourceFailureException("falha simulada")).when(emissao).emitir(anyLong(), any());

		enviar(comValor("termoProtocolo", "").file(foto(PNG)).file(termo(PDF)))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("Não foi possível salvar o cadastro. Tente novamente.")))
				.andExpect(content().string(containsString("value=\"Ana Teste\"")));

		assertThat(alunosNovos()).isZero();
		assertThat(contatos()).isZero();
		assertThat(consentimentos()).isZero();
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void transacaoRodaEDesfazOsBlocosJaGravados() throws IOException {
		// CA-RF04-02: o aluno e o vínculo foram gravados, e o contato falha (e-mail grande
		// demais para a coluna). Nada fica no banco nem na área privada
		NovoAluno comContatoInvalido = new NovoAluno("Ana Teste", MATRICULA_NOVA, idTurma, "Carla Teste",
				"+55 11 900000001", "a".repeat(300) + "@example.com");
		ByteArrayInputStream foto = new ByteArrayInputStream(PNG);
		ByteArrayInputStream termo = new ByteArrayInputStream(PDF);

		assertThatThrownBy(() -> cadastro.cadastrar(comContatoInvalido, TERMO_DIGITALIZADO, foto, termo, FUNCIONARIO))
				.isInstanceOf(DataAccessException.class);

		assertThat(alunosNovos()).isZero();
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void termoDigitalizadoFicaNaAreaPrivadaComAChaveNaReferencia() throws IOException {
		// RF05, passo 4
		ByteArrayInputStream foto = new ByteArrayInputStream(PNG);
		ByteArrayInputStream termo = new ByteArrayInputStream(PDF);
		BlocosGravados blocos = cadastro.cadastrar(novo(), TERMO_DIGITALIZADO, foto, termo, FUNCIONARIO);

		String referencia = jdbc.queryForObject("SELECT referencia_termo FROM consentimento WHERE id_consentimento = ?",
				String.class, blocos.idConsentimento());
		assertThat(referencia).matches("[0-9a-f-]{36}\\.pdf");
		assertThat(AREA_PRIVADA.resolve(referencia)).hasBinaryContent(PDF);
		assertThat(arquivosNaAreaPrivada()).isEqualTo(2);
	}

	@Test
	void conferenteEInstanteDaConferenciaVemDoServidor() {
		// CA-RF05-04: os blocos gravados dentro de uma transação desfeita no fim
		transacao.executeWithoutResult(status -> {
			status.setRollbackOnly();
			BlocosGravados blocos = cadastro.gravarBlocos(novo(), new TermoConferido("v1-2026",
					LocalDate.now().minusDays(1), "TERMO-0001"), "sem-arquivo.png", "TERMO-0001", FUNCIONARIO);

			Map<String, Object> consentimento = jdbc.queryForMap("SELECT * FROM consentimento WHERE id_consentimento = ?",
					blocos.idConsentimento());
			long idFuncionario = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Long.class,
					FUNCIONARIO);
			assertThat(((Number) consentimento.get("id_usuario_conferente")).longValue()).isEqualTo(idFuncionario);
			assertThat(((Number) consentimento.get("id_contato")).longValue()).isEqualTo(blocos.idContato());
			assertThat(consentimento).containsEntry("versao_termo", "v1-2026")
					.containsEntry("referencia_termo", "TERMO-0001")
					.containsEntry("revogado_em", null);
			assertThat((LocalDateTime) consentimento.get("conferido_em"))
					.isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
		});
	}

	@Test
	void valoresDeConferenteEnviadosPelaTelaSaoIgnorados() throws Exception {
		// CA-RF05-04: o formulário não tem esses campos; enviados à força, não mudam nada
		enviar(valido().file(foto(PNG)).param("idUsuarioConferente", "999999").param("conferidoEm", "2020-01-01"))
				.andExpect(status().is3xxRedirection());

		long idFuncionario = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = ?", Long.class,
				FUNCIONARIO);
		Map<String, Object> consentimento = jdbc.queryForMap("SELECT c.id_usuario_conferente, c.conferido_em "
				+ "FROM consentimento c JOIN contato_responsavel r ON r.id_contato = c.id_contato "
				+ "JOIN aluno a ON a.id_aluno = r.id_aluno WHERE a.matricula = ?", MATRICULA_NOVA);
		assertThat(((Number) consentimento.get("id_usuario_conferente")).longValue()).isEqualTo(idFuncionario);
		assertThat((LocalDateTime) consentimento.get("conferido_em"))
				.isCloseTo(LocalDateTime.now(ZoneOffset.UTC), within(1, ChronoUnit.MINUTES));
	}

	@Test
	void cadastroRegistraAlunoCadastradoSoComOId(CapturedOutput saida) throws Exception {
		// RF04, passo 7: o funcionário e o ID do aluno, sem dados pessoais completos
		enviar(valido().file(foto(PNG))).andExpect(status().is3xxRedirection());

		long aluno = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class, MATRICULA_NOVA);
		assertThat(saida.getOut().lines().filter(linha -> linha.contains("evento=ALUNO_CADASTRADO")))
				.singleElement().asString()
				.contains("aluno=" + aluno, "usuario=c***")
				.doesNotContain("carla.teste@example.com", MATRICULA_NOVA, "Ana Teste");
	}

	// --- Erros (CA-RF04-03, 04 e 09) ---

	@ParameterizedTest
	@CsvSource({
			"nome, Informe o nome do aluno.",
			"matricula, Informe a matrícula do aluno.",
			"idTurma, Selecione uma turma ativa.",
			"contatoNome, Informe o nome do responsável.",
			"contatoTelefone, 'Informe o telefone com DDD. Exemplo: (11) 90000-0000.'",
			"contatoEmail, 'Informe um e-mail válido. Exemplo: nome@example.com.'" })
	void campoVazioMostraOErroEPreservaOsOutros(String campo, String mensagem) throws Exception {
		String pagina = enviar(comValor(campo, "   ").file(foto(PNG)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		assertThat(pagina).contains(mensagem)
				.contains("id=\"" + campo + "-erro\"")
				.contains("aria-invalid=\"true\"")
				.contains("aria-describedby=\"" + campo + "-erro")
				// O resumo no topo leva ao campo
				.contains("id=\"resumo-erros\"", "href=\"#" + campo + "\"");
		if (!"nome".equals(campo)) {
			assertThat(pagina).contains("value=\"Ana Teste\"");
		}
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void semFotoORegistroEhRecusado() throws Exception {
		// CA-RF04-03; DEC-05
		enviar(valido())
				.andExpect(content().string(containsString("Envie a foto do aluno.")))
				.andExpect(content().string(containsString("aria-describedby=\"foto-erro foto-dica\"")))
				.andExpect(content().string(containsString("value=\"" + MATRICULA_NOVA + "\"")));
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void fotoForaDaRegraEhRecusadaPeloConteudo() throws Exception {
		// Um PDF com nome de imagem: o que vale é o conteúdo
		enviar(valido().file(new MockMultipartFile("foto", "ana.png", "image/png", PDF)))
				.andExpect(content().string(containsString("A foto precisa ser JPEG ou PNG, até 2 MB.")));
		enviar(valido().file(foto(Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1))))
				.andExpect(content().string(containsString("A foto precisa ser JPEG ou PNG, até 2 MB.")));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void todosOsErrosAparecemDeUmaVez() throws Exception {
		mvc.perform(multipart("/alunos").with(funcionario()).with(csrf()))
				.andExpect(content().string(containsString("Informe o nome do aluno.")))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")))
				.andExpect(content().string(containsString("Envie a foto do aluno.")))
				.andExpect(content().string(containsString("Informe o nome do responsável.")));
	}

	@Test
	void matriculaJaCadastradaEhRecusadaComAOrientacao() throws Exception {
		// A11 §5.9.9, §5.9.10. Zeros à esquerda fazem parte da matrícula
		enviar(comValor("matricula", MATRICULA_EXISTENTE).file(foto(PNG)))
				.andExpect(content().string(containsString("Esta matrícula já está cadastrada.")))
				.andExpect(content().string(containsString("localize o cadastro existente")));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void turmaInativaEhRecusada() throws Exception {
		enviar(comValor("idTurma", String.valueOf(idTurmaInativa)).file(foto(PNG)))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")));
		enviar(comValor("idTurma", "nao-e-numero").file(foto(PNG)))
				.andExpect(content().string(containsString("Selecione uma turma ativa.")));
	}

	@Test
	void telefoneEEmailInvalidosSaoRecusados() throws Exception {
		// CA-RF04-04; DEC-09
		enviar(comValor("contatoTelefone", "90000-0000").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoTelefone-erro\"")));
		enviar(comValor("contatoEmail", "carla.teste").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoEmail-erro\"")));
		enviar(comValor("contatoEmail", "carla@escola").file(foto(PNG)))
				.andExpect(content().string(containsString("id=\"contatoEmail-erro\"")));
	}

	@Test
	void textoComHtmlAparececomoTexto() throws Exception {
		// CA-RF04-11; A10 §8.28: no formulário com erro (sem foto) e na impressão, depois de salvo
		enviar(comValor("nome", "<b>Ana</b>"))
				.andExpect(content().string(containsString("&lt;b&gt;Ana&lt;/b&gt;")))
				.andExpect(content().string(not(containsString("<b>Ana</b>"))));

		String impressao = enviar(comValor("nome", "<b>Ana</b>").file(foto(PNG)))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getRedirectedUrl();
		mvc.perform(get(impressao).with(funcionario()))
				.andExpect(content().string(containsString("&lt;b&gt;Ana&lt;/b&gt;")))
				.andExpect(content().string(not(containsString("<b>Ana</b>"))));
	}

	// --- Termo de consentimento (RF05; CA-RF05-01 a 03, 05, 07 e 10) ---

	@Test
	void formularioTrazAVersaoVigenteEACaixaComTextoAssociado() throws Exception {
		// CA-RF05-10 e CA-RF05-07
		mvc.perform(get("/alunos/novo").with(funcionario()))
				.andExpect(content().string(containsString("value=\"v1-2026\"")))
				.andExpect(content().string(containsString("<label for=\"termoConferido\">Confirmo que conferi o termo "
						+ "de consentimento assinado pelo responsável (obrigatório)</label>")))
				.andExpect(content().string(containsString("PDF, JPEG ou PNG, até 5 MB")));
	}

	@Test
	void semACaixaMarcadaOAlunoNaoECadastrado() throws Exception {
		// CA-RF05-01
		enviar(comValor("termoConferido", "false").file(foto(PNG)))
				.andExpect(content().string(containsString("Confirme que o termo de consentimento assinado foi conferido.")))
				.andExpect(content().string(containsString("aria-describedby=\"termoConferido-erro\"")))
				.andExpect(status().isOk());
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void caixaMarcadaSemDataOuSemReferenciaNaoBasta() throws Exception {
		// CA-RF05-02: o checkbox sozinho não basta (A14 p.7)
		enviar(comValor("termoAssinadoEm", "").file(foto(PNG)))
				.andExpect(content().string(containsString("Informe a data da assinatura do termo.")));
		enviar(comValor("termoProtocolo", "").file(foto(PNG)))
				.andExpect(content().string(containsString("Informe o protocolo do termo físico ou envie a cópia "
						+ "digitalizada.")));
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void dataDaAssinaturaFuturaEhRecusada() throws Exception {
		// CA-RF05-03: depois da data local da conferência (fuso da escola)
		String amanha = LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA).plusDays(1).toString();
		enviar(comValor("termoAssinadoEm", amanha).file(foto(PNG)))
				.andExpect(content().string(containsString("A data da assinatura não pode ser posterior a hoje.")));
		// Hoje vale
		String hoje = LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA).toString();
		enviar(comValor("termoAssinadoEm", hoje).file(foto(PNG)))
				.andExpect(status().is3xxRedirection());
	}

	@Test
	void signatarioForcadoNaRequisicaoEhRecusado() throws Exception {
		// CA-RF05-05: um contato de outro aluno não pode ser o signatário
		long contatoDeOutroAluno = jdbc.queryForObject("SELECT MAX(id_aluno) FROM aluno", Long.class);
		enviar(comValor("termoSignatario", String.valueOf(contatoDeOutroAluno)).file(foto(PNG)))
				.andExpect(content().string(containsString("Não foi possível conferir o signatário do termo.")))
				.andExpect(status().isOk());
		assertThat(alunosNovos()).isZero();
	}

	@Test
	void termoDigitalizadoPassaPelaAreaPrivadaENaoFicaOrfao() throws Exception {
		// RF05, passo 4; CA-RF05-06 (a entrega autorizada está em ArquivoControllerTests)
		enviar(comValor("termoProtocolo", "").file(foto(PNG)).file(termo(PDF)))
				.andExpect(status().is3xxRedirection());
		// A foto e o termo ficam, cada um com o seu nome UUID
		assertThat(arquivosNaAreaPrivada()).isEqualTo(2);
	}

	@Test
	void termoDigitalizadoForaDaRegraEhRecusado() throws Exception {
		enviar(comValor("termoProtocolo", "").file(foto(PNG))
				.file(termo("<html>".getBytes(StandardCharsets.UTF_8))))
				.andExpect(content().string(containsString("O termo digitalizado precisa ser PDF, JPEG ou PNG, até 5 MB.")));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void protocoloEArquivoJuntosSaoRecusados() throws Exception {
		enviar(valido().file(foto(PNG)).file(termo(PDF)))
				.andExpect(content().string(containsString("Informe o protocolo ou envie a cópia digitalizada, não os dois.")));
	}

	// --- Acesso (CA-RF04-10) ---

	@Test
	void postSemLoginNaoGravaELevaAoLogin() throws Exception {
		mvc.perform(preenchido(multipart("/alunos")).file(foto(PNG)).with(csrf()))
				.andExpect(redirectedUrl("/login"));
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	@Test
	void postSemTokenCsrfEhRecusado() throws Exception {
		mvc.perform(preenchido(multipart("/alunos")).file(foto(PNG)).with(funcionario()))
				.andExpect(status().isForbidden());
		assertThat(arquivosNaAreaPrivada()).isZero();
	}

	// --- Apoio ---

	private MockMultipartHttpServletRequestBuilder valido() {
		return preenchido(multipart("/alunos"));
	}

	/** Todos os campos válidos, menos um. */
	private MockMultipartHttpServletRequestBuilder comValor(String campo, String valor) {
		MockMultipartHttpServletRequestBuilder pedido = multipart("/alunos");
		camposValidos().forEach((nome, valido) -> pedido.param(nome, nome.equals(campo) ? valor : valido));
		return pedido;
	}

	private MockMultipartHttpServletRequestBuilder preenchido(MockMultipartHttpServletRequestBuilder pedido) {
		camposValidos().forEach(pedido::param);
		return pedido;
	}

	private Map<String, String> camposValidos() {
		return Map.ofEntries(Map.entry("nome", "Ana Teste"),
				Map.entry("matricula", MATRICULA_NOVA),
				Map.entry("idTurma", String.valueOf(idTurma)),
				Map.entry("contatoNome", "Carla Teste"),
				Map.entry("contatoTelefone", "(11) 90000-0001"),
				Map.entry("contatoEmail", "carla.teste@example.com"),
				Map.entry("termoSignatario", "principal"),
				Map.entry("termoVersao", "v1-2026"),
				Map.entry("termoAssinadoEm", ontem().toString()),
				Map.entry("termoProtocolo", "TERMO-0001"),
				Map.entry("termoConferido", "true"));
	}

	private NovoAluno novo() {
		return new NovoAluno("Ana Teste", MATRICULA_NOVA, idTurma, "Carla Teste", "+55 11 900000001",
				"carla.teste@example.com");
	}

	/** Ontem no fuso da escola (RN10). */
	private static LocalDate ontem() {
		return LocalDate.now(RelogioAjustavel.FUSO_DA_ESCOLA).minusDays(1);
	}

	private int consentimentos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM consentimento c JOIN contato_responsavel r "
				+ "ON r.id_contato = c.id_contato WHERE r.email = 'carla.teste@example.com'", Integer.class);
	}

	private ResultActions enviar(MockMultipartHttpServletRequestBuilder pedido) throws Exception {
		return mvc.perform(pedido.with(funcionario()).with(csrf()));
	}

	private static MockMultipartFile foto(byte[] conteudo) {
		return new MockMultipartFile("foto", "ana.png", "image/png", conteudo);
	}

	private static MockMultipartFile termo(byte[] conteudo) {
		return new MockMultipartFile("termoArquivo", "termo.pdf", "application/pdf", conteudo);
	}

	private static RequestPostProcessor funcionario() {
		return user(FUNCIONARIO).roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

	private int alunosNovos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM aluno WHERE matricula = ?", Integer.class, MATRICULA_NOVA);
	}

	private int contatos() {
		return jdbc.queryForObject("SELECT COUNT(*) FROM contato_responsavel WHERE email = 'carla.teste@example.com'",
				Integer.class);
	}

	private long arquivosNaAreaPrivada() throws IOException {
		if (!Files.exists(AREA_PRIVADA)) {
			return 0;
		}
		try (var arquivos = Files.list(AREA_PRIVADA)) {
			return arquivos.count();
		}
	}

}
