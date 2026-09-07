package com.iceibank.agencia.service;

import org.springframework.stereotype.Service;

/**
 * Relogio logico de Lamport (1978): um contador inteiro por processo (aqui,
 * por agencia), com as tres regras classicas. O metodos sao "synchronized"
 * porque o Spring atende requisicoes HTTP em varias threads simultaneas, e o
 * contador e um estado compartilhado entre elas - sem isso, duas requisicoes
 * concorrentes poderiam incrementar o contador de forma inconsistente
 * (condicao de corrida).
 */
@Service
public class RelogioLamport {

    private int contador = 0;

    /** Regra 1: antes de qualquer evento local, incrementa o contador. */
    public synchronized int eventoLocal() {
        contador += 1;
        return contador;
    }

    /** Regra 2: ao enviar uma mensagem, incrementa e anexa o valor a mensagem. */
    public synchronized int aoEnviar() {
        contador += 1;
        return contador;
    }

    /** Regra 3: ao receber uma mensagem com timestamp t, ajusta para max(local, t) + 1. */
    public synchronized int aoReceber(int timestampRecebido) {
        contador = Math.max(contador, timestampRecebido) + 1;
        return contador;
    }

    public synchronized int valorAtual() {
        return contador;
    }
}
