package br.com.furin.access.historico;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.historico.ConsultaDoHistorico.Filtros;
import br.com.furin.access.historico.ConsultaDoHistorico.Pagina;
import br.com.furin.access.historico.ConsultaDoHistorico.Turma;

/**
 * {@code GET /historico} (T-35; RF18, RF19; arquitetura §4): os filtros e a tabela das
 * movimentações confirmadas. Só consulta: a tela não tem nenhuma ação de alteração
 * (CA-RF18-07; A13 p.4, item 2).
 * <p>
 * Os filtros vão na URL (formulário {@code GET}), com padrão "hoje" no fuso da escola
 * (DEC-28, C5), e continuam preenchidos depois da consulta e dos erros. Um valor manipulado
 * na turma ou na página é ignorado, sem erro (CA-RF18-06). A versão para impressão é da
 * T-36 (RF20).
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

	/** O que o formulário mostra: o valor como chegou, ou o padrão. */
	public record Formulario(String inicio, String fim, Long turma, String aluno) {
	}

	private final ConsultaDoHistorico consulta;

	private final Clock relogio;

	private final ZoneId fusoDaEscola;

	private final int periodoMaximo;

	public HistoricoController(ConsultaDoHistorico consulta, Clock relogio, FurinProperties propriedades) {
		this.consulta = consulta;
		this.relogio = relogio;
		this.fusoDaEscola = propriedades.escola().fuso();
		this.periodoMaximo = propriedades.historico().periodoMaxDias();
	}

	@GetMapping("/historico")
	public String consultar(@RequestParam(required = false) String inicio, @RequestParam(required = false) String fim,
			@RequestParam(required = false) String turma, @RequestParam(required = false) String aluno,
			@RequestParam(required = false) String pagina, Model model) {
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
		model.addAttribute("formulario", new Formulario(valor(inicio, dataInicial), valor(fim, dataFinal), idTurma,
				nomeOuMatricula == null ? "" : nomeOuMatricula));
		model.addAttribute("turmas", turmas);
		model.addAttribute("erros", erros);
		model.addAttribute("periodoMaximo", periodoMaximo);

		if (erros.isEmpty()) {
			Pagina resultado = consulta.consultar(
					new Filtros(dataInicial.orElseThrow(), dataFinal.orElseThrow(), idTurma, nomeOuMatricula),
					numero(pagina).map(Long::intValue).orElse(1));
			model.addAttribute("resultado", resultado);
			model.addAttribute("legenda", legenda(dataInicial.get(), dataFinal.get(), idTurma, turmas, nomeOuMatricula,
					resultado));
			model.addAttribute("total", total(resultado.total()));
		}
		return "historico/historico";
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

	/** RF19, passo 3: o resumo dos filtros, por exemplo "Entradas e saídas de 19/10/2026, turma 6A — Manhã". */
	private static String legenda(LocalDate inicio, LocalDate fim, Long idTurma, List<Turma> turmas, String aluno,
			Pagina resultado) {
		StringBuilder texto = new StringBuilder("Entradas e saídas ");
		texto.append(inicio.equals(fim) ? "de " + BRASIL.format(inicio)
				: "de " + BRASIL.format(inicio) + " a " + BRASIL.format(fim));
		texto.append(idTurma == null ? ", todas as turmas"
				: ", turma " + turmas.stream().filter(t -> t.id() == idTurma).findFirst().orElseThrow().nome());
		if (aluno != null) {
			texto.append(", aluno \"").append(aluno).append('"');
		}
		if (resultado.totalDePaginas() > 1) {
			texto.append(" (página ").append(resultado.pagina()).append(" de ").append(resultado.totalDePaginas())
					.append(')');
		}
		return texto.toString();
	}

	/** RF18, passo 6 e exceções. [PROP: singular] */
	private static String total(long total) {
		if (total == 0) {
			return "Nenhuma entrada ou saída encontrada para os filtros escolhidos.";
		}
		return total == 1 ? "1 registro encontrado" : total + " registros encontrados";
	}

}
