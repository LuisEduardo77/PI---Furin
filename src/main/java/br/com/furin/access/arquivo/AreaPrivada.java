package br.com.furin.access.arquivo;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import br.com.furin.access.arquivo.ArquivoRecusado.Motivo;
import br.com.furin.access.config.FurinProperties;
import br.com.furin.access.config.FurinProperties.Arquivos;
import br.com.furin.access.config.FurinProperties.RegraArquivo;

/**
 * Área privada de fotos e termos (T-18; DEC-08; RF04, RF05): os arquivos ficam em
 * {@code furin.arquivos.diretorio}, fora de {@code static/}, com nome UUID, e só saem pela
 * rota autorizada ({@link ArquivoController}).
 * <p>
 * O formato vem sempre do conteúdo (DEC-28, D1 e D2), ao gravar e ao entregar. Quem grava
 * guarda a chave devolvida em {@code aluno.foto_chave} ou {@code consentimento.referencia_termo}.
 */
@Service
public class AreaPrivada {

	/** A foto ou o termo, cada um com a sua regra em {@code furin.arquivos.*}. */
	public enum TipoDeDocumento {
		FOTO, TERMO
	}

	/** Um arquivo pronto para a entrega, com o tipo reconhecido pelo conteúdo. */
	public record ArquivoPrivado(ByteArrayResource conteudo, String mime) {
	}

	/** O formato dos nomes da área privada. Só isto chega ao sistema de arquivos. */
	private static final Pattern FORMATO_DA_CHAVE = Pattern
			.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png|pdf)");

	private static final Logger LOG = LoggerFactory.getLogger(AreaPrivada.class);

	private final Arquivos arquivos;

	private final Path diretorio;

	@Autowired
	public AreaPrivada(FurinProperties propriedades) {
		this(propriedades.arquivos());
	}

	AreaPrivada(Arquivos arquivos) {
		this.arquivos = arquivos;
		this.diretorio = arquivos.diretorio().toAbsolutePath().normalize();
	}

	/**
	 * Confere o tamanho e o tipo e grava com um nome UUID.
	 *
	 * @return a chave do arquivo
	 * @throws ArquivoRecusado se o arquivo estiver fora das regras; nada é gravado
	 */
	public String guardar(TipoDeDocumento documento, InputStream entrada) throws IOException {
		RegraArquivo regra = regra(documento);
		byte[] conteudo = lerAteOLimite(entrada, regra.tamanhoMax().toBytes());
		FormatoDeArquivo formato = formatoAceito(regra, conteudo);

		String chave = UUID.randomUUID() + formato.extensao();
		Files.createDirectories(diretorio);
		Files.write(diretorio.resolve(chave), conteudo, StandardOpenOption.CREATE_NEW);
		return chave;
	}

	/**
	 * Confere o arquivo sem gravar, para o formulário mostrar todos os erros de uma vez.
	 *
	 * @throws ArquivoRecusado se o arquivo estiver fora das regras
	 */
	public void conferir(TipoDeDocumento documento, InputStream entrada) throws IOException {
		RegraArquivo regra = regra(documento);
		formatoAceito(regra, lerAteOLimite(entrada, regra.tamanhoMax().toBytes()));
	}

	/** Tira da área privada um arquivo de um cadastro que não foi salvo (CA-RF04-02). */
	public void apagar(String chave) {
		if (!chaveValida(chave)) {
			return;
		}
		try {
			Files.deleteIfExists(diretorio.resolve(chave));
		} catch (IOException falha) {
			// Quem chamou já está tratando a falha original, que é a que importa
			LOG.warn("Arquivo órfão na área privada: {}", chave, falha);
		}
	}

	/**
	 * @return o arquivo, ou vazio se a chave for inválida, se o arquivo não existir ou não
	 *         puder ser lido, ou se o conteúdo não for de um formato conhecido
	 */
	public Optional<ArquivoPrivado> abrir(String chave) {
		if (!chaveValida(chave)) {
			return Optional.empty();
		}
		Path arquivo = diretorio.resolve(chave).normalize();
		if (!arquivo.startsWith(diretorio)) {
			return Optional.empty();
		}
		try {
			byte[] conteudo = Files.readAllBytes(arquivo);
			return FormatoDeArquivo.doConteudo(conteudo)
					.map(formato -> new ArquivoPrivado(new ByteArrayResource(conteudo), formato.mime()));
		} catch (IOException | UncheckedIOException semArquivo) {
			return Optional.empty();
		}
	}

	/** {@code false} para qualquer coisa que o sistema não poderia ter gerado. */
	public boolean chaveValida(String chave) {
		return chave != null && FORMATO_DA_CHAVE.matcher(chave).matches();
	}

	private RegraArquivo regra(TipoDeDocumento documento) {
		return documento == TipoDeDocumento.FOTO ? arquivos.foto() : arquivos.termo();
	}

	private static FormatoDeArquivo formatoAceito(RegraArquivo regra, byte[] conteudo) {
		return FormatoDeArquivo.doConteudo(conteudo)
				.filter(reconhecido -> regra.tipos().contains(reconhecido.mime()))
				.orElseThrow(() -> new ArquivoRecusado(Motivo.TIPO_NAO_ACEITO));
	}

	/** Lê um byte além do limite, para recusar sem carregar um arquivo grande inteiro. */
	private static byte[] lerAteOLimite(InputStream entrada, long limite) throws IOException {
		byte[] conteudo = entrada.readNBytes(Math.toIntExact(limite + 1));
		if (conteudo.length > limite) {
			throw new ArquivoRecusado(Motivo.TAMANHO_EXCEDIDO);
		}
		return conteudo;
	}

}
