package com.iceibank.agencia.service;

/** Compara dois vetores logicos: IGUAIS, ANTES (v1 <= v2), DEPOIS (v2 <= v1) ou CONCORRENTES. */
public final class CompararVetores {

    private CompararVetores() {
    }

    public static String comparar(int[] v1, int[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            throw new IllegalArgumentException("Vetores invalidos ou de tamanhos diferentes.");
        }
        boolean v1MenorOuIgual = true;
        boolean v2MenorOuIgual = true;
        for (int i = 0; i < v1.length; i++) {
            if (v1[i] > v2[i]) v1MenorOuIgual = false;
            if (v2[i] > v1[i]) v2MenorOuIgual = false;
        }
        if (v1MenorOuIgual && v2MenorOuIgual) return "IGUAIS";
        if (v1MenorOuIgual) return "ANTES";
        if (v2MenorOuIgual) return "DEPOIS";
        return "CONCORRENTES";
    }
}
