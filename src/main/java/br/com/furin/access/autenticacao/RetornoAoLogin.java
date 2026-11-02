package br.com.furin.access.autenticacao;

import java.io.IOException;
import java.util.Map;

import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Volta à tela de login depois de um erro (RF02, fluxo principal, passo 3.3; E3), no
 * padrão POST-redirecionamento-GET. O erro e o usuário digitado vão como atributos
 * "flash", que valem só para o próximo GET /login e chegam ao modelo da tela. A senha
 * nunca vai junto: o campo senha volta vazio (CA-RF02-07).
 */
final class RetornoAoLogin {

	static final String PAGINA_DE_LOGIN = "/login";

	/** Atributos lidos por templates/autenticacao/login.html. */
	static final String USUARIO_DIGITADO = "usuarioDigitado";
	static final String ERRO_CREDENCIAL = "erroCredencial";
	static final String ERRO_USUARIO_VAZIO = "erroUsuarioVazio";
	static final String ERRO_SENHA_VAZIA = "erroSenhaVazia";
	static final String ERRO_BLOQUEADO = "erroBloqueado";
	static final String ERRO_SENHA_VENCIDA = "erroSenhaVencida";

	/** Nenhum login válido passa disso (usuario.login tem 80); só limita o que vai para a sessão. */
	private static final int TAMANHO_MAXIMO_DO_USUARIO = 200;

	/** A mesma implementação que o DispatcherServlet usa para ler o flash no GET. */
	private static final FlashMapManager FLASH = new SessionFlashMapManager();

	private RetornoAoLogin() {
	}

	static void voltar(HttpServletRequest pedido, HttpServletResponse resposta, Map<String, Boolean> erros)
			throws IOException {
		String destino = pedido.getContextPath() + PAGINA_DE_LOGIN;
		FlashMap flash = new FlashMap();
		flash.put(USUARIO_DIGITADO, usuarioDigitado(pedido));
		flash.putAll(erros);
		flash.setTargetRequestPath(destino);
		FLASH.saveOutputFlashMap(flash, pedido, resposta);
		resposta.sendRedirect(destino);
	}

	/** O usuário como foi digitado, sem normalizar: a tela devolve o que a pessoa escreveu. */
	private static String usuarioDigitado(HttpServletRequest pedido) {
		String usuario = pedido.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY);
		if (usuario == null) {
			return "";
		}
		return usuario.length() > TAMANHO_MAXIMO_DO_USUARIO ? usuario.substring(0, TAMANHO_MAXIMO_DO_USUARIO) : usuario;
	}

}
