package com.iceibank.agencia.dto;

/** Resposta de GET /bully/lider. liderAtual e null enquanto nao ha lider conhecido. */
public record LiderResponse(int idAgencia, Integer liderAtual) {
}