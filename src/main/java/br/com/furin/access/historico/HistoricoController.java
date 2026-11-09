package br.com.furin.access.historico;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import java.text.NumberFormat;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.DetalhesAuditoria;
import br.com.furin.access.auditoria.EventoAuditoria;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.historico.ConsultaDoHistorico.Filtros;
import br.com.furin.access.historico.ConsultaDoHistorico.Pagina;
import br.com.furin.access.historico.ConsultaDoHistorico.Relatorio;
import br.com.furin.access.historico.ConsultaDoHistorico.Turma;

/**
 * O histórico (T-35; RF18, RF19) e a versão para impressão (T-36; RF20); arquitetura §4.
 * Só consulta: nenhuma das telas tem ação de alteração (CA-RF18-07; A13 p.4, item 2).
 * <ul>
 * <li>{@code GET /historico}: os filtros e a tabela, 50 por página;</li>
 * <li>{@code GET /historico/impressao} [PROP: rota]: os mesmos filtros e registros, sem
 * paginação, até {@code furin.relatorio.max-registros}, com cabeçalho de identificação, sem a
 * coluna "Aviso ao responsável" e com o rodapé de uso restrito. A abertura vai para o log
 * como {@code RELATORIO_IMPRESSO} (DEC-28, C6).</li>
 * </ul>
 * Os filtros vão na URL, com padrão "hoje" no fuso da escola (DEC-28, C5), e continuam
 * preenchidos depois da consulta e dos erros. Um valor manipulado na turma ou na página é
 * ignorado, sem erro (CA-RF18-06). Não há exportação para CSV ou planilha (CA-RF20-07).
 */
@Controller
public class HistoricoController {

	static final String CAMPO_INICIO = "inicio";
	static final String CAMPO_FIM = "fim";

	/** O mesmo tamanho de {@code aluno.nome}. */
	private static final int TAMANHO_MAXIMO_DO_ALUNO = 150;

	private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
	/** STRICT: 31/02 é recusado, em vez de virar 28/02. */
	private static final DateTimeFormatter BRASIL = DateTimeFormatter.ofPattern("dd/MM/uuuu")
			.withResolverStyle(ResolverStyle.STRICT);

	private static final String NENHUM_REGISTRO = "Nenhuma entrada ou saída encontrada para os filtros escolhidos.";

	/** O que o formulário mostra: o valor como chegou, ou o padrão. */
	public record Formulario(String inicio, String fim, Long turma, String aluno) {
	}

	/** Os filtros lidos da URL. {@code filtros} é nulo quando há erro. */
	private record Leitura(Formulario formulario, Map<String, String> erros, List<Turma> turmas, Filtros filtros) {
	}

	private final ConsultaDoHistorico consulta;

	private final Auditoria auditoria;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	private final int periodoMaximo;

	private final String nomeDaEscola;

	private final DateTimeFormatter emissao;

	public HistoricoController(ConsultaDoHistorico consulta, Auditoria auditoria, Clock relogio,
			FurinProperties propriedades) {
		this.consulta = consulta;
		this.auditoria = auditoria;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
		this.periodoMaximo = propriedades.historico().periodoMaxDias();
		this.nomeDaEscola = propriedades.escola().nome();
		this.emissao = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(fusoDaEscola);
	}

	@GetMapping("/historico")
	public String consultar(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim,
			@RequestParam(required = false) String turma, @RequestParam(required = false) String aluno,
			@RequestParam(required = false) String pagina, Model model) {
		Leitura leitura = ler(inicio, fim, turma, aluno);
		model.addAttribute("formulario", leitura.formulario());
		model.addAttribute("turmas", leitura.turmas());
		model.addAttribute("erros", leitura.erros());
		model.addAttribute("periodoMaximo", periodoMaximo);

		if (leitura.filtros() != null) {
			Pagina resultado = consulta.consultar(leitura.filtros(), numero(pagina).map(Long::intValue).orElse(1));
			model.addAttribute("resultado", resultado);
			String legenda = "Entradas e saídas " + filtrosEmTexto(leitura);
			if (resultado.totalDePaginas() > 1) {
				legenda += " (página " + resultado.pagina() + " de " + resultado.totalDePaginas() + ")";
			}
			model.addAttribute("legenda", legenda);
			model.addAttribute("total", total(resultado.total()));
		}
		return "historico/historico";
	}

	/** RF20: a versão para impressão, com os mesmos filtros da consulta. */
	@GetMapping("/historico/impressao")
	public String imprimir(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim,
			@RequestParam(required = false) String turma, @RequestParam(required = false) String aluno,
			Authentication funcionario, Model model) {
		Leitura leitura = ler(inicio, fim, turma, aluno);
		model.addAttribute("formulario", leitura.formulario());
		model.addAttribute("erros", leitura.erros());
		model.addAttribute("nomeDaEscola", nomeDaEscola);
		if (leitura.filtros() == null) {
			return "historico/impressao";
		}

		Relatorio relatorio = consulta.paraImpressao(leitura.filtros());
		Instant agora = relogio.instant();
		model.addAttribute("relatorio", relatorio);
		model.addAttribute("filtros", filtrosEmTexto(leitura));
		model.addAttribute("emitido", "Emitido em " + emissao.format(agora) + " por "
				+ consulta.nomeDoUsuario(funcionario.getName()));
		model.addAttribute("total", relatorio.total() == 0 ? NENHUM_REGISTRO
				: "Total: " + numeroBrasileiro(relatorio.total()) + (relatorio.total() == 1 ? " registro" : " registros"));
		if (relatorio.excedeu()) {
			// RF20, exceções [PROP: texto]
			model.addAttribute("excedeu", "O relatório tem mais de " + numeroBrasileiro(consulta.maximoDoRelatorio())
					+ " registros. Refine os filtros para imprimir.");
		} else {
			registrarImpressao(leitura.filtros(), relatorio.total());
		}
		return "historico/impressao";
	}

	/**
	 * DEC-28, C6 (A10 §8.15): o usuário vem da sessão; os filtros vão sem dado pessoal. Do
	 * aluno, só se o filtro foi usado, nunca o nome ou a matrícula digitados. [PROP]
	 */
	private void registrarImpressao(Filtros filtros, long total) {
		DetalhesAuditoria detalhes = detalhes().texto(CAMPO_INICIO, ISO.format(filtros.inicio()))
				.texto(CAMPO_FIM, ISO.format(filtros.fim()));
		if (filtros.idTurma() != null) {
			detalhes.id("turma", filtros.idTurma());
		}
		detalhes.texto("aluno", filtros.aluno() == null ? "TODOS" : "FILTRADO").id("registros", total);
		auditoria.registrar(EventoAuditoria.RELATORIO_IMPRESSO, detalhes);
	}

	private Leitura ler(String inicio, String fim, String turma, String aluno) {
		// LocalDate.ofInstant, e não relogio.withZone: o relógio dos testes fica sempre em UTC
		LocalDate hoje = LocalDate.ofInstant(relogio.instant(), fusoDaEscola);
		Map<String, String> erros = new LinkedHashMap<>();
		Optional<LocalDate> dataInicial = data(inicio, hoje, CAMPO_INICIO, erros);
		Optional<LocalDate> dataFinal = data(fim, hoje, CAMPO_FIM, erros);
		if (dataInicial.isPresent() && dataFinal.isPresent()) {
			conferirPeriodo(dataInicial.get(), dataFinal.get(), erros);
		}
		List<Turma> turmas = consulta.turmas();
		Long idTurma = numero(turma).filter(id -> turmas.stream().anyMatch(t -> t.id() == id)).orElse(null);
		String nomeOuMatricula = aluno(aluno);
		Formulario formulario = new Formulario(valor(inicio, dataInicial), valor(fim, dataFinal), idTurma,
				nomeOuMatricula == null ? "" : nomeOuMatricula);
		Filtros filtros = erros.isEmpty()
				? new Filtros(dataInicial.orElseThrow(), dataFinal.orElseThrow(), idTurma, nomeOuMatricula)
				: null;
		return new Leitura(formulario, erros, turmas, filtros);
	}

	/** Vazio vale "hoje" (DEC-28, C5). Aceita o valor do campo de data e o dd/mm/aaaa digitado. */
	private Optional<LocalDate> data(String valor, LocalDate hoje, String campo, Map<String, String> erros) {
		if (valor == null || valor.isBlank()) {
			return Optional.of(hoje);
		}
		String limpo = valor.strip();
		for (DateTimeFormatter formato : List.of(ISO, BRASIL)) {
			try {
				return Optional.of(LocalDate.parse(limpo, formato));
			} catch (DateTimeParseException formatoDiferente) {
				// tenta o próximo formato
			}
		}
		erros.put(campo, "Informe a data no formato dd/mm/aaaa.");
		return Optional.empty();
	}

	/** RF18, exceções: as mensagens ficam no campo da data final. */
	private void conferirPeriodo(LocalDate inicio, LocalDate fim, Map<String, String> erros) {
		if (fim.isBefore(inicio)) {
			erros.put(CAMPO_FIM, "A data final precisa ser igual ou posterior à data inicial.");
		} else if (ChronoUnit.DAYS.between(inicio, fim) + 1 > periodoMaximo) {
			erros.put(CAMPO_FIM, "Escolha um período de até " + periodoMaximo + " dias.");
		}
	}

	/** O campo de data recebe aaaa-mm-dd; um valor que não é data volta como chegou. */
	private static String valor(String recebido, Optional<LocalDate> data) {
		return data.map(ISO::format).orElse(recebido == null ? "" : recebido.strip());
	}

	private static Optional<Long> numero(String valor) {
		if (valor == null || !valor.strip().matches("\\d{1,9}")) {
			return Optional.empty();
		}
		return Optional.of(Long.parseLong(valor.strip()));
	}

	private static String aluno(String valor) {
		if (valor == null || valor.isBlank()) {
			return null;
		}
		String limpo = valor.strip();
		return limpo.length() > TAMANHO_MAXIMO_DO_ALUNO ? limpo.substring(0, TAMANHO_MAXIMO_DO_ALUNO) : limpo;
	}

	/** RF19, passo 3, e RF20: "de 19/10/2026, turma 6A — Manhã (2026), aluno "Ana"". */
	private static String filtrosEmTexto(Leitura leitura) {
		Filtros filtros = leitura.filtros();
		StringBuilder texto = new StringBuilder(filtros.inicio().equals(filtros.fim())
				? "de " + BRASIL.format(filtros.inicio())
				: "de " + BRASIL.format(filtros.inicio()) + " a " + BRASIL.format(filtros.fim()));
		texto.append(filtros.idTurma() == null ? ", todas as turmas"
				: ", turma " + leitura.turmas().stream().filter(t -> t.id() == filtros.idTurma()).findFirst()
						.orElseThrow().nome());
		if (filtros.aluno() != null) {
			texto.append(", aluno \"").append(filtros.aluno()).append('"');
		}
		return texto.toString();
	}

	/** RF18, passo 6 e exceções. [PROP: singular] */
	private static String total(long total) {
		if (total == 0) {
			return NENHUM_REGISTRO;
		}
		return total == 1 ? "1 registro encontrado" : total + " registros encontrados";
	}

	/** "1.000", como no RF20. */
	private static String numeroBrasileiro(long numero) {
		return NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR")).format(numero);
	}

}
