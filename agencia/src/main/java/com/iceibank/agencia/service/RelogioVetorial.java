package com.iceibank.agencia.service;

import com.iceibank.agencia.config.AgenciaProperties;
import org.springframework.stereotype.Service;

@Service
public class RelogioVetorial {

    private final int idAgencia;
    private final int[] vetor;

    public RelogioVetorial(AgenciaProperties agenciaProperties) {
        this.idAgencia = agenciaProperties.getIdAgencia();
        this.vetor = new int[AgenciaProperties.NUMERO_AGENCIAS];
    }

    public synchronized int[] eventoLocal() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoEnviar() {
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] aoReceber(int[] vetorRecebido) {
        if (vetorRecebido == null || vetorRecebido.length != vetor.length) {
            throw new IllegalArgumentException(
                    "Vetor recebido inválido: esperado tamanho " + vetor.length);
        }
        for (int i = 0; i < vetor.length; i++) {
            vetor[i] = Math.max(vetor[i], vetorRecebido[i]);
        }
        vetor[idAgencia] += 1;
        return vetor.clone();
    }

    public synchronized int[] valorAtual() {
        return vetor.clone();
    }
}