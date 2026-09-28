package com.iceibank.agencia.dto;

public record CreditoRemotoRequest(double valor, int[] vetorEnvio, int origemAgencia) {
}