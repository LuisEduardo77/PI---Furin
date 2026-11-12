package br.com.furin.access.autenticacao;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;

/**
 * Contador de falhas e bloqueio do login (T-14; RF02, passos 4 e 5; DEC-28, E2).
 * <p>
 * A chave é o login normalizado, <b>exista a conta ou não</b> (RF02, passo 3.1): assim o
 * bloqueio funciona igual para logins inexistentes (CA-RF02-08). Depois de
 * {@code furin.login.max-tentativas} falhas seguidas, o login fica bloqueado durante
 * {@code furin.login.bloqueio}. Um login com sucesso zera o contador (CA-RF02-05).
 * <p>
 * Fica em memória, como sugere o RF02 [PROP]: o protótipo tem uma só instância, e um
 * reinício da aplicação zera todos os contadores. O horário vem do {@link Clock} da
 * aplicação (RelogioConfig), que os testes controlam.
 */
@Component
public class TentativasDeLogin {

	/** Falhas seguidas de um login e, se ele estiver bloqueado, até quando. */
	private record Situacao(int falhas, Instant bloqueadoAte) {
	}

	private final Map<String, Situacao> porLogin = new ConcurrentHashMap<>();

	private final Clock relogio;

	private final FurinProperties.Login regras;

	public TentativasDeLogin(Clock relogio, FurinProperties propriedades) {
		this.relogio = relogio;
		this.regras = propriedades.login();
	}

	/**
	 * O login está bloqueado agora? Quando o bloqueio vence, o login volta a ter todas as
	 * tentativas (CA-RF02-04) [PROP].
	 */
	public boolean bloqueado(String login) {
		Instant agora = relogio.instant();
		Situacao situacao = porLogin.computeIfPresent(chave(login),
				(chave, atual) -> venceu(atual, agora) ? null : atual);
		return situacao != null && situacao.bloqueadoAte() != null;
	}

	/**
	 * Soma uma falha ao login (RF02, passo 3.1).
	 *
	 * @return o fim do bloqueio, se esta falha bloqueou o login (RF02, passo 4); senão,
	 *         {@code null}
	 */
	public Instant registrarFalha(String login) {
		Instant agora = relogio.instant();
		AtomicReference<Instant> bloqueouAte = new AtomicReference<>();
		porLogin.compute(chave(login), (chave, atual) -> {
			Situacao valendo = atual == null || venceu(atual, agora) ? new Situacao(0, null) : atual;
			if (valendo.bloqueadoAte() != null) {
				// Já bloqueado: a tentativa é recusada antes de chegar aqui, e não prolonga o bloqueio
				return valendo;
			}
			int falhas = valendo.falhas() + 1;
			if (falhas < regras.maxTentativas()) {
				return new Situacao(falhas, null);
			}
			bloqueouAte.set(agora.plus(regras.bloqueio()));
			return new Situacao(falhas, bloqueouAte.get());
		});
		return bloqueouAte.get();
	}

	/** Login com sucesso: zera o contador (RF02, passo 5; CA-RF02-05). */
	public void registrarSucesso(String login) {
		porLogin.remove(chave(login));
	}

	/**
	 * Só para os testes, que dividem o mesmo contexto do Spring: começa sem nenhum contador.
	 * Público para os testes de outros pacotes que fazem login (T-43).
	 */
	public void esquecerTodos() {
		porLogin.clear();
	}

	private static boolean venceu(Situacao situacao, Instant agora) {
		return situacao.bloqueadoAte() != null && !agora.isBefore(situacao.bloqueadoAte());
	}

	private static String chave(String login) {
		return ContasDosFuncionarios.normalizarLogin(login);
	}

}
