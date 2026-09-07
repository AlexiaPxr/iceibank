package com.iceibank.agencia.exception;

public class ContaJaExisteException extends RuntimeException {
    public ContaJaExisteException(String mensagem) {
        super(mensagem);
    }
}
