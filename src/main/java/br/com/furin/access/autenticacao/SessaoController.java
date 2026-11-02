package br.com.furin.access.autenticacao;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * "Continuar conectado" (T-16; RF01, fluxo A2, passo 2): o próprio pedido renova a
 * sessão, porque cada acesso zera a contagem de inatividade. É POST, com o token CSRF
 * no cabeçalho (static/js/sessao.js). Sem corpo na resposta.
 */
@Controller
public class SessaoController {

	/** Rota da renovação (arquitetura §4). */
	public static final String RENOVAR = "/sessao/renovar";

	@PostMapping(RENOVAR)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void renovar() {
		// Nada a fazer: o acesso já renovou a sessão
	}

}
