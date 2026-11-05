package br.com.furin.access.arquivo;

import static br.com.furin.access.auditoria.DetalhesAuditoria.detalhes;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.furin.access.arquivo.AreaPrivada.ArquivoPrivado;
import br.com.furin.access.auditoria.Auditoria;
import br.com.furin.access.auditoria.EventoAuditoria;

/**
 * {@code GET /arquivos/{chave}} (T-18; DEC-08): entrega fotos e termos só a um funcionário
 * autenticado, pela regra {@code /arquivos/**} de {@code SegurancaConfig}. Sem login, o
 * pedido leva à tela de login (CA-RF04-06, CA-RF05-06, CA-RF10-05).
 * <p>
 * O tipo da resposta vem do conteúdo do arquivo. Os cabeçalhos do Spring Security completam
 * a entrega: {@code nosniff} e {@code no-store}, para o navegador não guardar a foto de um
 * aluno. Um arquivo que falta vai para o log (RF10; DEC-28, A9); a tela mostra "Foto
 * indisponível", e a liberação continua.
 */
@RestController
public class ArquivoController {

	private final AreaPrivada areaPrivada;

	private final Auditoria auditoria;

	public ArquivoController(AreaPrivada areaPrivada, Auditoria auditoria) {
		this.areaPrivada = areaPrivada;
		this.auditoria = auditoria;
	}

	@GetMapping("/arquivos/{chave}")
	public ResponseEntity<Resource> entregar(@PathVariable String chave) {
		if (!areaPrivada.chaveValida(chave)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND);
		}
		ArquivoPrivado arquivo = areaPrivada.abrir(chave).orElseThrow(() -> {
			auditoria.registrar(EventoAuditoria.ARQUIVO_INDISPONIVEL, detalhes().texto("chave", chave));
			return new ResponseStatusException(HttpStatus.NOT_FOUND);
		});
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(arquivo.mime()))
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
				.body(arquivo.conteudo());
	}

}
