package br.com.furin.access.config;

import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import br.com.furin.access.auditoria.AcessoNegadoAuditado;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.autenticacao.AtualizacaoAutomatica;
import br.com.furin.access.autenticacao.CamposDoLoginObrigatorios;
import br.com.furin.access.autenticacao.ContaDesativada;
import br.com.furin.access.autenticacao.ContasDosFuncionarios;
import br.com.furin.access.autenticacao.LoginComFalhaAuditado;
import br.com.furin.access.autenticacao.LoginBloqueado;
import br.com.furin.access.autenticacao.LoginComSucessoAuditado;
import br.com.furin.access.autenticacao.SessaoController;
import br.com.furin.access.autenticacao.SessaoExpirada;
import br.com.furin.access.autenticacao.TemposDaSessao;
import br.com.furin.access.autenticacao.TentativasDeLogin;
import br.com.furin.access.autenticacao.TrocaDeSenhaObrigatoria;
import jakarta.servlet.DispatcherType;

/**
 * Spring Security base (T-09; RF03, RNF03, RNF04; arquitetura-e-dados.md §6).
 * <p>
 * Negar por padrão: só as rotas da tabela "Rotas principais" (arquitetura §4) ficam
 * liberadas, e só para o perfil FUNCIONARIO (DEC-37). Sem login, qualquer pedido leva
 * ao login. Com login, uma rota com controller que não está na lista recebe 403
 * (escopo-e-perfis §6), e uma rota que não existe recebe 404 (DEC-42).
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
			// "Continuar conectado" do aviso de expiração (T-16)
			SessaoController.RENOVAR,
			// Temporária (T-08): sai quando existir a primeira tela real
			"/exemplo/layout"
	};

	/** Arquivos estáticos (static/), públicos. */
	static final String[] ARQUIVOS_ESTATICOS = { "/css/**", "/js/**", "/img/**", "/favicon.ico" };

	/** Públicos: a tela de login (T-12) e os arquivos estáticos. */
	static final String[] ROTAS_PUBLICAS = Stream.concat(Stream.of("/login"), Arrays.stream(ARQUIVOS_ESTATICOS))
			.toArray(String[]::new);

	/** Tela inicial de todos os funcionários (RF03; DEC-37). */
	public static final String TELA_INICIAL = "/portaria";

	/** Login depois de a sessão expirar por inatividade (T-16; RF01, fluxo A2, passo 3). */
	public static final String LOGIN_SESSAO_EXPIRADA = "/login?expirada";

	/** Login depois de a conta ser desativada durante a sessão (T-17; RF03, exceções). */
	public static final String LOGIN_CONTA_DESATIVADA = "/login?desativada";

	/** Cookie da sessão, apagado no "Sair" (T-16). */
	private static final String COOKIE_DA_SESSAO = "JSESSIONID";

	@Bean
	SecurityFilterChain filtroDeSeguranca(HttpSecurity http, AcessoNegadoAuditado acessoNegado,
			Auditoria auditoria, TentativasDeLogin tentativas, ContasDosFuncionarios contas,
			TemposDaSessao temposDaSessao, Clock relogio,
			@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping rotasDosControllers) {
		http
				.authorizeHttpRequests(rotas -> rotas
						// O despacho de erro (404, 500) vem de um pedido que já passou pela autorização
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(ROTAS_PUBLICAS).permitAll()
						.requestMatchers(ROTAS_DO_FUNCIONARIO).hasRole(PERFIL_FUNCIONARIO)
						// Negar por padrão (A10 §5.15; RF03, CA-RF03-04); rota inexistente dá 404 (DEC-42)
						.anyRequest().access(new NegarRotaExistenteSemRegra(rotasDosControllers)))
				// Tela própria em GET /login (autenticacao/LoginController); o POST /login é do
				// Spring Security, com as contas do banco (autenticacao/ContasDosFuncionarios)
				.formLogin(login -> login
						.loginPage("/login")
						.successHandler(new LoginComSucessoAuditado(auditoria, tentativas, TELA_INICIAL))
						// RF02: mensagem única e LOGIN_FALHA no log (T-13); conta a falha (T-14)
						.failureHandler(new LoginComFalhaAuditado(auditoria, tentativas)))
				// Antes do login, nesta ordem e depois do CSRF:
				// RF02, E3: campo vazio volta à tela sem consultar a conta (T-13)
				.addFilterBefore(new CamposDoLoginObrigatorios(), UsernamePasswordAuthenticationFilter.class)
				// RF02, E2: login bloqueado é recusado sem conferir a senha (T-14)
				.addFilterBefore(new LoginBloqueado(tentativas, auditoria), UsernamePasswordAuthenticationFilter.class)
				// RF03 (T-17): conta desativada durante a sessão encerra a sessão. Antes da troca
				// de senha e da autorização: nenhuma rota passa com a conta desativada
				.addFilterBefore(new ContaDesativada(contas, auditoria, LOGIN_CONTA_DESATIVADA),
						AuthorizationFilter.class)
				// RF01, fluxo A1 (T-15): com a troca pendente, toda rota leva a /senha/definir,
				// menos o "Sair", o "Continuar conectado" (T-16) e os arquivos estáticos. Antes
				// da autorização: até uma rota negada leva à troca (CA-RF01-04)
				.addFilterBefore(new TrocaDeSenhaObrigatoria(Stream.concat(Stream.of("/logout", SessaoController.RENOVAR),
						Arrays.stream(ARQUIVOS_ESTATICOS)).toArray(String[]::new)), AuthorizationFilter.class)
				// RF01, fluxo A2 (T-16): um pedido com o cookie de uma sessão que expirou por
				// inatividade leva ao login com o aviso. Antes do CSRF: o token do formulário
				// era da sessão antiga
				.addFilterBefore(new SessaoExpirada(LOGIN_SESSAO_EXPIRADA), CsrfFilter.class)
				// RF16 (T-34): a atualização automática da tela Presentes não prolonga a sessão
				.addFilterBefore(new AtualizacaoAutomatica(temposDaSessao, relogio, LOGIN_SESSAO_EXPIRADA),
						CsrfFilter.class)
				// POST /logout com o token CSRF; depois, volta ao login. O cookie é apagado
				// para que o próximo pedido não pareça uma sessão expirada (T-16)
				.logout(sair -> sair.deleteCookies(COOKIE_DA_SESSAO))
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
