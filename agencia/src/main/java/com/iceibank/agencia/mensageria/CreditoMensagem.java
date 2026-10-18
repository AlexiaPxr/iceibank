package com.iceibank.agencia.mensageria;

/**
 * Corpo da mensagem publicada na exchange quando uma agencia credita a conta
 * de outra. Substitui o CreditoRemotoRequest do Sprint 1 (que era o corpo de
 * uma chamada REST) - agora carrega tambem o idConta, porque nao ha mais URL
 * com o :id do destino, so a routing key que identifica a agencia.
 */
public record CreditoMensagem(int idConta, double valor, int[] vetorEnvio, int origemAgencia) {
}