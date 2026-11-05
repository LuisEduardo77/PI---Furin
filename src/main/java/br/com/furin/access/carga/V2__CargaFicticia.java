package br.com.furin.access.carga;

import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.SegurancaConfig;

/**
 * Carga fictícia do protótipo (T-11; arquitetura §5.6; DEC-28, G2; DEC-37).
 * <p>
 * Migração Java do Flyway, registrada como bean e só no perfil {@code prototipo}: roda
 * uma vez, numa transação, e fica no histórico como V2. É Java, e não SQL, porque as
 * senhas das contas de demonstração vêm do ambiente e precisam virar hash bcrypt
 * ({@link PasswordEncoder}), e porque fotos e termo fictícios são arquivos na área
 * privada (DEC-08).
 * <p>
 * Sem as variáveis {@value #VARIAVEL_SENHA_ATE} e {@value #VARIAVEL_SENHA_GESTAO}, ou
 * com uma senha menor que {@code furin.senha.tamanho-minimo}, a aplicação não sobe.
 * Só dados fictícios (A10 §8.33).
 */
@Component
@Profile("prototipo")
public class V2__CargaFicticia extends BaseJavaMigration {

	static final String VARIAVEL_SENHA_ATE = "FURIN_SENHA_ATE_DEMO";
	static final String VARIAVEL_SENHA_GESTAO = "FURIN_SENHA_GESTAO_DEMO";

	private final PasswordEncoder encoder;
	private final FurinProperties furin;
	private final Clock relogio;
	private final Environment ambiente;
	private final SecureRandom aleatorio = new SecureRandom();

	public V2__CargaFicticia(PasswordEncoder encoder, FurinProperties furin, Clock relogio, Environment ambiente) {
		this.encoder = encoder;
		this.furin = furin;
		this.relogio = relogio;
		this.ambiente = ambiente;
	}

	@Override
	public void migrate(Context contexto) throws SQLException {
		// As senhas são conferidas antes de gravar qualquer coisa
		String senhaAte = senhaDoAmbiente(VARIAVEL_SENHA_ATE);
		String senhaGestao = senhaDoAmbiente(VARIAVEL_SENHA_GESTAO);

		List<Path> arquivosCriados = new ArrayList<>();
		try {
			new Carga(contexto.getConnection(), arquivosCriados).executar(senhaAte, senhaGestao);
		} catch (SQLException | RuntimeException falha) {
			// A transação do Flyway desfaz o banco; aqui só se apagam os arquivos já criados
			arquivosCriados.forEach(V2__CargaFicticia::apagarArquivo);
			throw falha;
		}
	}

	/** Lê a senha do ambiente (ou do application-local.yml), sem nunca mostrar o valor. */
	String senhaDoAmbiente(String variavel) {
		String senha = ambiente.getProperty(variavel);
		if (senha == null || senha.isBlank()) {
			throw new IllegalStateException("Carga fictícia: defina a variável de ambiente " + variavel
					+ " com a senha da conta de demonstração (veja o README).");
		}
		int minimo = furin.senha().tamanhoMinimo();
		if (senha.length() < minimo) {
			throw new IllegalStateException("Carga fictícia: a senha em " + variavel
					+ " precisa ter pelo menos " + minimo + " caracteres (furin.senha.tamanho-minimo).");
		}
		return senha;
	}

	/** Os inserts da carga, numa conexão só (a da transação do Flyway). */
	private final class Carga {

		private final Connection conexao;
		private final List<Path> arquivosCriados;
		private final LocalDateTime agora;

		Carga(Connection conexao, List<Path> arquivosCriados) {
			this.conexao = conexao;
			this.arquivosCriados = arquivosCriados;
			// Gravado em UTC (RN10); LocalDateTime não passa pela conversão de fuso do driver
			this.agora = LocalDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
		}

		void executar(String senhaAte, String senhaGestao) throws SQLException {
			long ate = usuario("ate.demo", "ATE Demo", senhaAte);
			usuario("gestao.demo", "Gestão Demo", senhaGestao);

			long turma3B = turma("3B");
			long turma6A = turma("6A");

			String versao = furin.termo().versaoAtual();

			// Carla Teste é a responsável pelos irmãos Ana e Davi: um contato para cada aluno (§5.6)
			long ana = aluno("000123", "Ana Teste", turma6A, new Color(0xDC, 0xE8, 0xF2));
			long contatoAna = contato(ana, "Carla Teste", "(11) 90000-0001", "carla.teste@example.com", true);
			consentimento(contatoAna, versao, termoDigitalizado(versao), ate);
			carteirinha(ana);

			long davi = aluno("000124", "Davi Teste", turma3B, new Color(0xE4, 0xEF, 0xE0));
			long contatoDavi = contato(davi, "Carla Teste", "(11) 90000-0001", "carla.teste@example.com", true);
			consentimento(contatoDavi, versao, "Protocolo fictício 0002", ate);
			carteirinha(davi);

			// Bruno Exemplo pediu para não receber o aviso por e-mail (DEC-38)
			long bia = aluno("000200", "Bia Exemplo", turma6A, new Color(0xF2, 0xE6, 0xDC));
			long contatoBia = contato(bia, "Bruno Exemplo", "(11) 90000-0002", "bruno.pai@example.com", false);
			consentimento(contatoBia, versao, "Protocolo fictício 0003", ate);
			carteirinha(bia);
		}

		private long usuario(String login, String nome, String senha) throws SQLException {
			// Contas de demonstração: sem troca obrigatória, para a demonstração funcionar direto [PROP]
			return inserir("INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, "
					+ "troca_senha_obrigatoria, senha_temporaria_expira_em, criado_em) VALUES (?, ?, ?, ?, 1, 0, NULL, ?)",
					nome, login, encoder.encode(senha), SegurancaConfig.PERFIL_FUNCIONARIO, agora);
		}

		private long turma(String nome) throws SQLException {
			return inserir("INSERT INTO turma (nome, ano_letivo, turno, ativa) VALUES (?, ?, ?, 1)",
					nome, 2026, "MANHA");
		}

		private long aluno(String matricula, String nome, long turma, Color fundoDaFoto) throws SQLException {
			String foto = gravarArquivo(".png", ArquivosFicticios.silhuetaPng(fundoDaFoto));
			long aluno = inserir("INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em) VALUES (?, ?, ?, 1, ?)",
					matricula, nome, foto, agora);
			inserir("INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em, fim_em) VALUES (?, ?, ?, NULL)",
					aluno, turma, agora);
			return aluno;
		}

		private long contato(long aluno, String nome, String telefone, String email, boolean recebeAviso)
				throws SQLException {
			return inserir("INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, ativo, principal, "
					+ "recebe_notificacao) VALUES (?, ?, ?, ?, 1, 1, ?)",
					aluno, nome, telefone, email, recebeAviso ? 1 : 0);
		}

		private void consentimento(long contato, String versao, String referencia, long conferente)
				throws SQLException {
			inserir("INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, "
					+ "id_usuario_conferente, conferido_em, revogado_em) VALUES (?, ?, ?, ?, ?, ?, NULL)",
					contato, versao, LocalDate.from(agora), referencia, conferente, agora);
		}

		private void carteirinha(long aluno) throws SQLException {
			// Cada byte vira 2 caracteres hexadecimais (furin.carteirinha.tamanho-codigo; A14 p.6)
			byte[] bytes = new byte[furin.carteirinha().tamanhoCodigo() / 2];
			aleatorio.nextBytes(bytes);
			inserir("INSERT INTO carteirinha (id_aluno, codigo, emitida_em, expira_em, revogada_em) "
					+ "VALUES (?, ?, ?, NULL, NULL)", aluno, HexFormat.of().formatHex(bytes), agora);
		}

		/** O termo de exemplo vai para a área privada; a chave fica em referencia_termo (RF05). */
		private String termoDigitalizado(String versao) {
			return gravarArquivo(".pdf", ArquivosFicticios.termoPdf(versao));
		}

		/** Grava na área privada com nome UUID (DEC-08) e devolve a chave. */
		private String gravarArquivo(String extensao, byte[] conteudo) {
			String chave = UUID.randomUUID() + extensao;
			Path diretorio = furin.arquivos().diretorio();
			try {
				Files.createDirectories(diretorio);
				Path arquivo = Files.write(diretorio.resolve(chave), conteudo);
				arquivosCriados.add(arquivo);
			} catch (IOException e) {
				throw new UncheckedIOException("Carga fictícia: não foi possível gravar em " + diretorio, e);
			}
			return chave;
		}

		private long inserir(String sql, Object... valores) throws SQLException {
			try (PreparedStatement comando = conexao.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
				for (int i = 0; i < valores.length; i++) {
					comando.setObject(i + 1, valores[i]);
				}
				comando.executeUpdate();
				try (ResultSet chaves = comando.getGeneratedKeys()) {
					return chaves.next() ? chaves.getLong(1) : 0;
				}
			}
		}

	}

	/** Apaga um arquivo criado pela carga, quando a carga falha no meio. */
	private static void apagarArquivo(Path arquivo) {
		try {
			Files.deleteIfExists(arquivo);
		} catch (IOException e) {
			// Sobra só um arquivo fictício órfão na área privada; a falha original é a que importa
		}
	}

}
