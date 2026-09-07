package com.iceibank.agencia.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Centraliza a traducao das excecoes de negocio para respostas HTTP no
 * mesmo formato usado no roteiro original: { "erro": "mensagem" }.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ContaNaoEncontradaException.class)
    public ResponseEntity<Map<String, String>> tratar(ContaNaoEncontradaException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(ContaJaExisteException.class)
    public ResponseEntity<Map<String, String>> tratar(ContaJaExisteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(ContaForaDaParticaoException.class)
    public ResponseEntity<Map<String, String>> tratar(ContaForaDaParticaoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<Map<String, String>> tratar(SaldoInsuficienteException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(FalhaTransferenciaRemotaException.class)
    public ResponseEntity<Map<String, String>> tratar(FalhaTransferenciaRemotaException e) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<Map<String, String>> tratar(CredenciaisInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("erro", e.getMessage()));
    }
}
