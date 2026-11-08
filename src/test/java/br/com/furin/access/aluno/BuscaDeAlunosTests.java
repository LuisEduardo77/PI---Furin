package br.com.furin.access.aluno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.aluno.BuscaDeAlunos.AlunoEncontrado;
import br.com.furin.access.aluno.BuscaDeAlunos.Escopo;
import br.com.furin.access.aluno.BuscaDeAlunos.Resultado;
import br.com.furin.access.aluno.BuscaDeAlunos.Situacao;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Busca por nome ou matrícula (T-24; RF11; CA-RF11-01 a 08). Os nomes da cena têm a marca
 * "Zzbusca", para não se misturar com outros dados do banco de testes. Cada teste roda na
 * transação do teste, desfeita no fim.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BuscaDeAlunosTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private BuscaDeAlunos busca;

	private long turma;
	private long usuario;

	@BeforeEach
	void preparar() {
		LocalDateTime ontem = LocalDateTime.now(ZoneOffset.UTC).minusDays(1);
		jdbc.update("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, "
				+ "senha_temporaria_expira_em, criado_em) VALUES ('Funcionário Teste', 'busca.teste', '{noop}x', ?, 1, 0, "
				+ "NULL, ?)", SegurancaConfig.PERFIL_FUNCIONARIO, ontem);
		usuario = jdbc.queryForObject("SELECT id_usuario FROM usuario WHERE login = 'busca.teste'", Long.class);
		jdbc.update("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES ('6A-T24', 2026, 'MANHA', 1)");
		turma = jdbc.queryForObject("SELECT id_turma FROM turma WHERE nome = '6A-T24'", Long.class);
		aluno("0024123", "Ana Zzbusca", true);
		aluno("0024124", "Ána Zzbusca Lima", true);
		aluno("0024125", "Bia 50%_x Zzbusca", true);
		aluno("0024126", "Caio Zzbusca", false);
	}

	// --- CA-RF11-01: nome sem caixa nem acento ---

	@Test
	void anaEncontraAnaEAnaComAcento() {
		assertThat(nomes(busca.buscar("ana zzb", Escopo.SO_ATIVOS))).containsExactly("Ana Zzbusca", "Ána Zzbusca Lima");
		assertThat(nomes(busca.buscar("ÁNA ZZBUSCA", Escopo.SO_ATIVOS))).containsExactly("Ana Zzbusca",
				"Ána Zzbusca Lima");
	}

	// --- CA-RF11-02: matrícula por prefixo, com os zeros à esquerda ---

	@Test
	void matriculaPorPrefixoComOsZerosAEsquerda() {
		assertThat(nomes(busca.buscar("0024123", Escopo.SO_ATIVOS))).containsExactly("Ana Zzbusca");
		assertThat(nomes(busca.buscar("002412", Escopo.SO_ATIVOS)))
				.containsExactly("Ana Zzbusca", "Ána Zzbusca Lima", "Bia 50%_x Zzbusca");
		// "24123" não é o começo de "0024123"
		assertThat(nomes(busca.buscar("24123", Escopo.SO_ATIVOS))).doesNotContain("Ana Zzbusca");
	}

	// --- CA-RF11-03: % e _ como texto ---

	@Test
	void porcentoESublinhadoSaoTextoENaoCuringa() {
		assertThat(nomes(busca.buscar("50%_x", Escopo.SO_ATIVOS))).containsExactly("Bia 50%_x Zzbusca");
		// Como curinga, "Zz%" e "Zzbusc_" encontrariam todos; como texto, nenhum
		assertThat(busca.buscar("Zz%", Escopo.SO_ATIVOS).alunos()).isEmpty();
		assertThat(busca.buscar("Zzbusc_", Escopo.SO_ATIVOS).alunos()).isEmpty();
		assertThat(busca.buscar("' OR '1'='1", Escopo.SO_ATIVOS).alunos()).isEmpty();
	}

	@Test
	void escapaOsCaracteresEspeciaisDoLike() {
		assertThat(BuscaDeAlunos.escapar("50%_a!b")).isEqualTo("50!%!_a!!b");
	}

	// --- CA-RF11-04: inativos ---

	@Test
	void inativosSoComOFiltroEComASituacaoInativo() {
		assertThat(nomes(busca.buscar("Caio Zzb", Escopo.SO_ATIVOS))).isEmpty();
		assertThat(busca.buscar("Caio Zzb", Escopo.COM_INATIVOS).alunos()).singleElement()
				.extracting(AlunoEncontrado::situacao).isEqualTo(Situacao.INATIVO);
	}

	// --- RN12: situação ---

	@Test
	void situacaoSegueARn12ComASaidaPendenteDeDiaAnterior() {
		long na = aluno("0024200", "Davi Zzpresenca", true);
		long pendente = aluno("0024201", "Edu Zzpresenca", true);
		aluno("0024202", "Fabi Zzpresenca", true);
		confirmada(na, "ENTRADA", LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
		confirmada(pendente, "ENTRADA", LocalDateTime.now(ZoneOffset.UTC).minusDays(2));

		assertThat(busca.buscar("Zzpresenca", Escopo.SO_ATIVOS).alunos()).extracting(AlunoEncontrado::situacao)
				.containsExactly(Situacao.NA_ESCOLA, Situacao.SAIDA_PENDENTE, Situacao.FORA_DA_ESCOLA);
	}

	// --- Limite e termo ---

	@Test
	void noMaximoVinteResultadosEmOrdemDeNome() {
		for (int i = 1; i <= 21; i++) {
			aluno("00249" + String.format("%02d", i), String.format("Lote %02d Zzlimite", i), true);
		}

		Resultado resultado = busca.buscar("Zzlimite", Escopo.SO_ATIVOS);

		assertThat(resultado.alunos()).hasSize(20);
		assertThat(resultado.limiteAtingido()).isTrue();
		assertThat(resultado.alunos().getFirst().nome()).isEqualTo("Lote 01 Zzlimite");
	}

	@Test
	void termoVazioOuCurtoDemaisEhRecusado() {
		assertThat(busca.problemaNoTermo("   ")).contains("Digite o nome ou a matrícula do aluno.");
		assertThat(busca.problemaNoTermo("a")).contains("Digite pelo menos 2 caracteres do nome.");
		// Um dígito é o começo de uma matrícula
		assertThat(busca.problemaNoTermo("0")).isEmpty();
		assertThatThrownBy(() -> busca.buscar("a", Escopo.SO_ATIVOS)).isInstanceOf(IllegalArgumentException.class);
	}

	// --- Tela Alunos (CA-RF11-04 a 08) ---

	@Test
	void telaMostraOsResultadosComAQuantidadeEmRoleStatus() throws Exception {
		mvc.perform(get("/alunos").param("termo", "ana zzb").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("<label for=\"termo\">Buscar aluno por nome ou matrícula</label>")))
				.andExpect(content().string(containsString("<div role=\"status\">")))
				.andExpect(content().string(containsString("2 alunos encontrados.")))
				.andExpect(content().string(containsString("<th scope=\"row\">Ana Zzbusca</th>")))
				.andExpect(content().string(containsString("6A-T24 — Manhã")))
				// CA-RF11-06
				.andExpect(content().string(containsString(">Editar aluno Ana Zzbusca</a>")));
	}

	@Test
	void resultadosSemFotoESemDadosDoResponsavel() throws Exception {
		// CA-RF11-05
		mvc.perform(get("/alunos").param("termo", "Ana Zzb").with(funcionario()))
				.andExpect(content().string(not(containsString("/arquivos/"))))
				.andExpect(content().string(not(containsString("<img"))))
				.andExpect(content().string(not(containsString("Carla Zzresponsavel"))))
				.andExpect(content().string(not(containsString("zzresponsavel@example.com"))));
	}

	@Test
	void inativosNaTelaSoComOFiltro() throws Exception {
		mvc.perform(get("/alunos").param("termo", "Caio Zzb").with(funcionario()))
				// O termo volta escapado (th:text), nunca como HTML
				.andExpect(content().string(containsString("Nenhum aluno encontrado para &#39;Caio Zzb&#39;.")));
		mvc.perform(get("/alunos").param("termo", "Caio Zzb").param("inativos", "true").with(funcionario()))
				.andExpect(content().string(containsString("1 aluno encontrado.")))
				.andExpect(content().string(containsString("<td>Inativo</td>")))
				.andExpect(content().string(containsString("checked")));
	}

	@Test
	void termoCurtoMostraOErroNoCampo() throws Exception {
		mvc.perform(get("/alunos").param("termo", "a").with(funcionario()))
				.andExpect(content().string(containsString("Digite pelo menos 2 caracteres do nome.")))
				.andExpect(content().string(containsString("aria-describedby=\"termo-erro termo-dica\"")))
				.andExpect(content().string(containsString("aria-invalid=\"true\"")));
	}

	@Test
	void semTermoATelaAbreSemBuscaNemMensagem() throws Exception {
		mvc.perform(get("/alunos").with(funcionario()))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("href=\"/alunos/novo\"")))
				.andExpect(content().string(not(containsString("encontrado"))))
				.andExpect(content().string(not(containsString("aria-invalid"))));
	}

	@Test
	void semLoginABuscaLevaAoLogin() throws Exception {
		// CA-RF11-08
		mvc.perform(get("/alunos").param("termo", "ana")).andExpect(redirectedUrl("/login"));
	}

	// --- Apoio ---

	private long aluno(String matricula, String nome, boolean ativo) {
		LocalDateTime ontem = LocalDateTime.now(ZoneOffset.UTC).minusDays(3);
		jdbc.update("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, ?, ?, ?, ?)",
				matricula, nome, UUID.randomUUID() + ".png", ativo, ontem);
		long id = jdbc.queryForObject("SELECT id_aluno FROM aluno WHERE matricula = ?", Long.class, matricula);
		jdbc.update("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (?, ?, ?)", id, turma, ontem);
		// Um contato, para conferir que ele não aparece na busca
		jdbc.update("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
				+ "recebe_notificacao) VALUES (?, 'Carla Zzresponsavel', '+55 11 900000001', "
				+ "'zzresponsavel@example.com', 1, 1, 1)", id);
		return id;
	}

	private void confirmada(long aluno, String tipo, LocalDateTime quando) {
		long vinculo = jdbc.queryForObject("SELECT id_vinculo FROM vinculo_turma WHERE id_aluno = ?", Long.class, aluno);
		jdbc.update("INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (?, ?, ?)", aluno,
				UUID.randomUUID().toString().replace("-", ""), quando.minusDays(5));
		long carteirinha = jdbc.queryForObject("SELECT id_carteirinha FROM carteirinha WHERE id_aluno = ?", Long.class,
				aluno);
		jdbc.update("INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, "
				+ "lancado_em, ocorrido_em, chave_requisicao) VALUES (?, ?, ?, ?, 'LEITURA', 'CONFIRMADA', ?, ?, ?)",
				vinculo, usuario, carteirinha, tipo, quando, quando, UUID.randomUUID().toString());
	}

	private static java.util.List<String> nomes(Resultado resultado) {
		return resultado.alunos().stream().map(AlunoEncontrado::nome).toList();
	}

	private static RequestPostProcessor funcionario() {
		return user("busca.teste").roles(SegurancaConfig.PERFIL_FUNCIONARIO);
	}

}
