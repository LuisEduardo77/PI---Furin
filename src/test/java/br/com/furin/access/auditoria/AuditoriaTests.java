package br.com.furin.access.auditoria;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Formato e regras do log de auditoria (T-10; A10 §8.15; RNF03; RNF05).
 * Só dados fictícios.
 */
@ExtendWith(OutputCaptureExtension.class)
class AuditoriaTests {

	private static final String SENHA_DE_TESTE = "senha-de-teste-T10";
	private static final String EMAIL = "carla.teste@example.com";
	private static final String CODIGO = "0123456789abcdef0123456789abcdef";

	private final Auditoria auditoria = new Auditoria(
			Clock.fixed(Instant.parse("2026-11-01T17:00:00Z"), ZoneOffset.UTC));

	@AfterEach
	void limparUsuario() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void linhaTemEventoHorarioUsuarioEIds(CapturedOutput saida) {
		autenticar("ate.demo");
		auditoria.registrar(EventoAuditoria.ALUNO_EDITADO, detalhes()
				.id("idAluno", 12)
				.campos(List.of("nome", "turma")));

		assertThat(saida.getOut()).contains(
				"evento=ALUNO_EDITADO quando=2026-11-01T17:00:00Z usuario=a*** idAluno=12 campos=nome,turma");
	}

	@Test
	void nenhumaSenhaApareceNoLog(CapturedOutput saida) {
		// A senha está nas credenciais do usuário autenticado; o log não pode mostrá-la
		autenticar(EMAIL);
		auditoria.registrar(EventoAuditoria.SENHA_ALTERADA, detalhes().id("idUsuario", 7));
		auditoria.registrarComo(EventoAuditoria.LOGIN_FALHA, EMAIL, detalhes().texto("origem", "127.0.0.1"));

		assertThat(saida.getOut())
				.contains("evento=SENHA_ALTERADA", "evento=LOGIN_FALHA")
				.doesNotContain(SENHA_DE_TESTE);
	}

	@Test
	void emailECodigoSaemMascarados(CapturedOutput saida) {
		autenticar(EMAIL);
		auditoria.registrar(EventoAuditoria.NOTIFICACAO_SIMULADA, detalhes()
				.id("idRegistro", 3)
				.email("destino", "bia.teste@example.com"));
		auditoria.registrar(EventoAuditoria.LEITURA_RECUSADA, detalhes()
				.texto("motivo", "CARTEIRINHA_REVOGADA")
				.codigoCarteirinha(CODIGO));

		assertThat(saida.getOut())
				.contains("usuario=c***@example.com", "destino=b***@example.com", "codigo=0123***")
				.doesNotContain(EMAIL, "bia.teste@example.com", CODIGO, "01234");
	}

	@Test
	void semLoginOUsuarioEhAnonimo(CapturedOutput saida) {
		auditoria.registrar(EventoAuditoria.ACESSO_NEGADO, detalhes().texto("rota", "/alunos"));
		assertThat(saida.getOut()).contains("usuario=anonimo rota=/alunos");
	}

	@Test
	void valorComQuebraDeLinhaNaoForjaOutraLinha(CapturedOutput saida) {
		auditoria.registrar(EventoAuditoria.ACESSO_NEGADO,
				detalhes().texto("rota", "/x\nevento=CONTA_CRIADA usuario=admin"));

		assertThat(saida.getOut())
				.contains("rota=\"/x_evento=CONTA_CRIADA usuario=admin\"")
				.doesNotContain("\nevento=CONTA_CRIADA");
	}

	@Test
	void nomeDeDetalheInvalidoEhRecusado() {
		DetalhesAuditoria detalhes = detalhes();
		assertThatThrownBy(() -> detalhes.texto("rota falsa", "x")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> detalhes.id("1id", 1)).isInstanceOf(IllegalArgumentException.class);
	}

	private static void autenticar(String login) {
		SecurityContextHolder.getContext().setAuthentication(
				new TestingAuthenticationToken(login, SENHA_DE_TESTE, "ROLE_FUNCIONARIO"));
	}

}
