package br.com.furin.access.autenticacao;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.furin.access.config.FurinProperties;

/**
 * Aceite do Termo de uso e da Política de privacidade (DEC-47): no primeiro acesso, e sempre
 * que a versão de {@code furin.termos.versao-atual} mudar, o funcionário aceita os
 * termos antes de usar o sistema. A conta guarda a última versão aceita e o instante (UTC).
 */
@Service
public class AceiteDosTermos {

	private final UsuarioRepository usuarios;

	private final Clock relogio;

	private final String versaoAtual;

	public AceiteDosTermos(UsuarioRepository usuarios, Clock relogio, FurinProperties propriedades) {
		this.usuarios = usuarios;
		this.relogio = relogio;
		this.versaoAtual = propriedades.termos().versaoAtual();
	}

	public String versaoAtual() {
		return versaoAtual;
	}

	/** A conta ainda não aceitou a versão atual dos termos? */
	@Transactional(readOnly = true)
	public boolean pendente(String login) {
		return usuarios.findByLogin(ContasDosFuncionarios.normalizarLogin(login))
				.map(conta -> !conta.aceitouOsTermos(versaoAtual))
				.orElse(true);
	}

	/** Grava o aceite da versão atual, agora. */
	@Transactional
	public void aceitar(String login) {
		Usuario conta = usuarios.findByLogin(ContasDosFuncionarios.normalizarLogin(login))
				.orElseThrow(() -> new IllegalStateException("Conta do funcionário autenticado não encontrada"));
		conta.aceitarTermos(versaoAtual, relogio.instant());
		usuarios.saveAndFlush(conta);
	}

}
