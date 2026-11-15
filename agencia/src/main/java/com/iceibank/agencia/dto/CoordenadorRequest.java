package com.iceibank.agencia.dto;

/** Corpo de POST /bully/coordenador: quem se declarou lider e o vetor do envio. */
public record CoordenadorRequest(int liderId, int[] vetorEnvio) {
}