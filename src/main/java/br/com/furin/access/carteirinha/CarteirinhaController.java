package br.com.furin.access.carteirinha;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.furin.access.carteirinha.Carteirinhas.AlunoInativo;
import br.com.furin.access.carteirinha.Carteirinhas.ParaImprimir;
import br.com.furin.access.carteirinha.EmissaoDeCarteirinha.CarteirinhaNaoEmitida;

/**
 * Carteirinha (T-22; RF07; RN15):
 * <ul>
 * <li>{@code GET /carteirinhas/{id}/imprimir}: o cartão de 85,6 × 54 mm, com nome, turma e QR
 * Code, sem foto e sem o código em texto (DEC-28, D8);</li>
 * <li>{@code GET /carteirinhas/{id}/qr-code}: a imagem do QR Code [PROP], só de carteirinha
 * válida. Uma imagem separada porque a CSP não aceita imagens embutidas;</li>
 * <li>{@code GET /alunos/{id}/carteirinhas/segunda-via}: a confirmação (A11 §5.9.12) [PROP],
 * enquanto não existe a página do aluno (T-23);</li>
 * <li>{@code POST /alunos/{id}/carteirinhas}: a segunda via.</li>
 * </ul>
 * Todas só para funcionário autenticado (regras {@code /carteirinhas/**} e {@code /alunos/**}).
 */
@Controller
public class CarteirinhaController {

	private static final String TELA_SEGUNDA_VIA = "carteirinhas/segunda-via";

	private final Carteirinhas carteirinhas;

	private final QrCodeDaCarteirinha qrCode;

	public CarteirinhaController(Carteirinhas carteirinhas, QrCodeDaCarteirinha qrCode) {
		this.carteirinhas = carteirinhas;
		this.qrCode = qrCode;
	}

	@GetMapping("/carteirinhas/{id}/imprimir")
	public String imprimir(@PathVariable long id, Model model) {
		model.addAttribute("carteirinha", paraImprimir(id));
		return "carteirinhas/imprimir";
	}

	@GetMapping("/carteirinhas/{id}/qr-code")
	public ResponseEntity<byte[]> qrCode(@PathVariable long id) {
		ParaImprimir carteirinha = paraImprimir(id);
		if (carteirinha.revogada()) {
			// Uma carteirinha revogada não deve ser impressa de novo (RN15)
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(qrCode.png(carteirinha.codigo()));
	}

	@GetMapping("/alunos/{idAluno}/carteirinhas/segunda-via")
	public String confirmarSegundaVia(@PathVariable long idAluno, Model model) {
		model.addAttribute("nomeDoAluno", nomeDoAluno(idAluno));
		model.addAttribute("idAluno", idAluno);
		model.addAttribute("carteirinhaAtual", carteirinhas.valida(idAluno).orElse(null));
		return TELA_SEGUNDA_VIA;
	}

	@PostMapping("/alunos/{idAluno}/carteirinhas")
	public String emitirSegundaVia(@PathVariable long idAluno, Model model, RedirectAttributes redirecionamento) {
		String nome = nomeDoAluno(idAluno);
		try {
			long nova = carteirinhas.emitirSegundaVia(idAluno);
			redirecionamento.addFlashAttribute("segundaViaEmitida", true);
			return "redirect:/carteirinhas/" + nova + "/imprimir";
		} catch (AlunoInativo inativo) {
			model.addAttribute("erro", "Não é possível emitir carteirinha para aluno inativo.");
		} catch (CarteirinhaNaoEmitida naoEmitida) {
			model.addAttribute("erro", "Não foi possível emitir a carteirinha. Tente novamente.");
		}
		model.addAttribute("nomeDoAluno", nome);
		model.addAttribute("idAluno", idAluno);
		model.addAttribute("carteirinhaAtual", carteirinhas.valida(idAluno).orElse(null));
		return TELA_SEGUNDA_VIA;
	}

	private ParaImprimir paraImprimir(long id) {
		return carteirinhas.paraImprimir(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private String nomeDoAluno(long idAluno) {
		return carteirinhas.nomeDoAluno(idAluno).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

}
