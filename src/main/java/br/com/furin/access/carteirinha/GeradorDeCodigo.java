package br.com.furin.access.carteirinha;

/**
 * Gera o código de uma carteirinha (RF07; RN15). Interface para o teste da colisão
 * (CA-RF06-07) poder repetir um código de propósito.
 */
@FunctionalInterface
public interface GeradorDeCodigo {

	String gerar();

}
