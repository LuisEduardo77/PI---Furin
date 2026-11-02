package br.com.furin.access.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import br.com.furin.access.auditoria.AcessoNegadoAuditado;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.autenticacao.CamposDoLoginObrigatorios;
import br.com.furin.access.autenticacao.LoginComFalhaAuditado;
import br.com.furin.access.autenticacao.LoginComSucessoAuditado;
import jakarta.servlet.DispatcherType;

/**
 * Spring Security base (T-09; RF03, RNF03, RNF04; arquitetura-e-dados.md §6).
 * <p>
 * Negar por padrão: só as rotas da tabela "Rotas principais" (arquitetura §4) ficam
 * liberadas, e só para o perfil FUNCIONARIO (DEC-37). Sem login, qualquer pedido leva
 * ao login. Com login, uma rota que não está na lista recebe 403 (escopo-e-perfis §6).
 * CSRF fica ligado (padrão do Spring Security).
 */
@Configuration
public class SegurancaConfig {

	/** Único perfil do sistema (DEC-37). Gravado pelo servidor, nunca vindo da tela. */
	public static final String PERFIL_FUNCIONARIO = "FUNCIONARIO";

	/** Rotas da arquitetura §4, todas do perfil FUNCIONARIO. Rota nova entra aqui. */
	static final String[] ROTAS_DO_FUNCIONARIO = {
			"/senha/definir", "/senha/alterar",
			"/portaria/**",
			"/alunos/**", "/carteirinhas/**",
			"/arquivos/**",
			"/presentes/**",
			"/historico/**",
			"/contas/equipe/**",
			// Temporária (T-08): sai quando existir a primeira tela real
			"/exemplo/layout"
	};

	/** Públicos: a tela de login (T-12) e os arquivos estáticos. */
	static final String[] ROTAS_PUBLICAS = { "/login", "/css/**", "/js/**", "/img/**", "/favicon.ico" };

	/** Tela inicial de todos os funcionários (RF03; DEC-37). */
	public static final String TELA_INICIAL = "/portaria";

	@Bean
	SecurityFilterChain filtroDeSeguranca(HttpSecurity http, AcessoNegadoAuditado acessoNegado,
			Auditoria auditoria) {
		http
				.authorizeHttpRequests(rotas -> rotas
						// O despacho de erro (404, 500) vem de um pedido que já passou pela autorização
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(ROTAS_PUBLICAS).permitAll()
						.requestMatchers(ROTAS_DO_FUNCIONARIO).hasRole(PERFIL_FUNCIONARIO)
						// Negar por padrão (A10 §5.15; RF03, CA-RF03-04)
						.anyRequest().denyAll())
				// Tela própria em GET /login (autenticacao/LoginController); o POST /login é do
				// Spring Security, com as contas do banco (autenticacao/ContasDosFuncionarios)
				.formLogin(login -> login
						.loginPage("/login")
						.successHandler(new LoginComSucessoAuditado(auditoria, TELA_INICIAL))
						// RF02: mensagem única e LOGIN_FALHA no log (T-13)
						.failureHandler(new LoginComFalhaAuditado(auditoria)))
				// RF02, E3: campo vazio volta à tela sem consultar a conta. Depois do CSRF
				.addFilterBefore(new CamposDoLoginObrigatorios(), UsernamePasswordAuthenticationFilter.class)
				// POST /logout com o token CSRF; depois, volta ao login
				.logout(Customizer.withDefaults())
				// Toda resposta 403 vai para o log de auditoria como ACESSO_NEGADO (T-10)
				.exceptionHandling(excecoes -> excecoes.accessDeniedHandler(acessoNegado))
				// Cabeçalhos padrão do Spring Security (nosniff, X-Frame-Options DENY,
				// Cache-Control no-store, HSTS só em HTTPS) e mais estes:
				.headers(cabecalhos -> cabecalhos
						.contentSecurityPolicy(csp -> csp.policyDirectives(
								"default-src 'self'; object-src 'none'; base-uri 'self'; "
										+ "form-action 'self'; frame-ancestors 'none'"))
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.SAME_ORIGIN)));
		return http.build();
	}

	/** RNF03; DEC-28 (G5): delegante, com bcrypt por padrão e o custo padrão da biblioteca. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

}
