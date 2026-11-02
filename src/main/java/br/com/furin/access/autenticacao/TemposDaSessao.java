package br.com.furin.access.autenticacao;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.furin.access.config.FurinProperties;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Tempo de inatividade da sessão e antecedência do aviso (T-16; RF01, fluxo A2; DEC-28,
 * E2; A11 §5.16.2), vindos de {@code furin.sessao.inatividade} e
 * {@code furin.sessao.aviso-antes}.
 * <p>
 * Como {@link HttpSessionListener}, aplica a inatividade a cada sessão criada, em
 * segundos. A propriedade {@code server.servlet.session.timeout} do Spring Boot não é
 * usada: o Tomcat arredonda esse valor para minutos.
 */
@Component
public class TemposDaSessao implements HttpSessionListener {

	private final Duration inatividade;

	private final Duration avisoAntes;

	@Autowired
	public TemposDaSessao(FurinProperties propriedades) {
		this(propriedades.sessao().inatividade(), propriedades.sessao().avisoAntes());
	}

	/** Para os testes, que precisam de tempos menores do que a validação da T-07 aceita. */
	TemposDaSessao(Duration inatividade, Duration avisoAntes) {
		this.inatividade = inatividade;
		this.avisoAntes = avisoAntes;
	}

	public long inatividadeEmSegundos() {
		return inatividade.toSeconds();
	}

	public long avisoAntesEmSegundos() {
		return avisoAntes.toSeconds();
	}

	@Override
	public void sessionCreated(HttpSessionEvent evento) {
		evento.getSession().setMaxInactiveInterval(Math.toIntExact(inatividadeEmSegundos()));
	}

}
