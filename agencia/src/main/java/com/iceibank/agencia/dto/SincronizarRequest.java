package com.iceibank.agencia.dto;

public record SincronizarRequest(String tipo, double valor, int[] vetorConhecido) {
}
