package com.iceibank.agencia.dto;

/** Corpo de POST /bully/eleicao: quem pergunta e o vetor do envio. */
public record EleicaoRequest(int origemId, int[] vetorEnvio) {
}