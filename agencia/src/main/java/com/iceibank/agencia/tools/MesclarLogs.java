package com.iceibank.agencia.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Linha do tempo causal (Sprint 2, Parte D). Le os .jsonl de todas as agencias,
 * ordena por hora de parede e compara os vetores (timestampVetorial) de cada par
 * de eventos de agencias diferentes:
 *   - ANTES / DEPOIS: um vetor e <= ao outro em todas as posicoes (relacao causal);
 *   - CONCORRENTES: nenhum vetor e <= ao outro (nenhum evento influenciou o outro).
 *
 * Executar a partir da pasta agencia/:
 *   mvn compile exec:java "-Dexec.mainClass=com.iceibank.agencia.tools.MesclarLogs"
 */
public class MesclarLogs {

    enum Relacao { IGUAIS, ANTES, DEPOIS, CONCORRENTES }

    record Evento(String agencia, String tipo, int[] vetor, Instant horaParede, Object detalhes) {
        String descricao() {
            return "[" + agencia + "] " + tipo + " " + Arrays.toString(vetor);
        }
    }

    static Relacao comparar(int[] v1, int[] v2) {
        boolean v1MenorOuIgual = true;
        boolean v2MenorOuIgual = true;
        for (int i = 0; i < v1.length; i++) {
            if (v1[i] > v2[i]) v1MenorOuIgual = false;
            if (v2[i] > v1[i]) v2MenorOuIgual = false;
        }
        if (v1MenorOuIgual && v2MenorOuIgual) return Relacao.IGUAIS;
        if (v1MenorOuIgual) return Relacao.ANTES;
        if (v2MenorOuIgual) return Relacao.DEPOIS;
        return Relacao.CONCORRENTES;
    }

    public static void main(String[] args) throws IOException {
        Path pastaDados = Path.of("data");
        if (!Files.isDirectory(pastaDados)) {
            System.out.println("Pasta 'data' não encontrada em " + pastaDados.toAbsolutePath());
            System.out.println("Rode este script a partir da pasta agencia/.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Evento> eventos = new ArrayList<>();
        int ignorados = 0;

        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> bruto = mapper.readValue(linha, Map.class);

                    // Eventos do Sprint 1 (timestampLamport) nao tem vetor: nao sao comparaveis.
                    if (!(bruto.get("timestampVetorial") instanceof List<?> lista)) {
                        ignorados++;
                        continue;
                    }
                    int[] vetor = lista.stream().mapToInt(o -> ((Number) o).intValue()).toArray();
                    eventos.add(new Evento(
                            String.valueOf(bruto.get("agencia")),
                            String.valueOf(bruto.get("tipo")),
                            vetor,
                            Instant.parse(String.valueOf(bruto.get("horaParede"))),
                            bruto.get("detalhes")));
                }
            }
        }

        if (eventos.isEmpty()) {
            System.out.println("Nenhum evento com vetor encontrado em " + pastaDados.toAbsolutePath());
            return;
        }
        if (ignorados > 0) {
            System.out.println("(" + ignorados + " evento(s) antigos sem vetor foram ignorados."
                    + " Apague data/*.jsonl para uma análise limpa.)\n");
        }

        eventos.sort(Comparator.comparing(Evento::horaParede));

        System.out.println("=== Linha do tempo (ordenada por hora de parede) ===");
        for (Evento e : eventos) {
            System.out.println(e.descricao() + " " + e.detalhes());
        }

        System.out.println("\n=== Pares CONCORRENTES entre agências diferentes ===");
        int concorrentes = 0;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = i + 1; j < eventos.size(); j++) {
                Evento a = eventos.get(i);
                Evento b = eventos.get(j);
                if (a.agencia().equals(b.agencia()) || a.vetor().length != b.vetor().length) continue;
                if (comparar(a.vetor(), b.vetor()) == Relacao.CONCORRENTES) {
                    concorrentes++;
                    System.out.println(a.descricao() + "  x  " + b.descricao());
                }
            }
        }
        if (concorrentes == 0) {
            System.out.println("(nenhum par concorrente nesta execução - gere mais eventos independentes e rode de novo)");
        }

        System.out.println("\n=== Pares CAUSALMENTE relacionados entre agências diferentes (A aconteceu antes de B) ===");
        int causais = 0;
        for (int i = 0; i < eventos.size(); i++) {
            for (int j = 0; j < eventos.size(); j++) {
                Evento a = eventos.get(i);
                Evento b = eventos.get(j);
                if (i == j || a.agencia().equals(b.agencia()) || a.vetor().length != b.vetor().length) continue;
                if (comparar(a.vetor(), b.vetor()) == Relacao.ANTES) {
                    causais++;
                    System.out.println(a.descricao() + "  ->  " + b.descricao());
                }
            }
        }
        if (causais == 0) {
            System.out.println("(nenhum par causal entre agências nesta execução)");
        }
    }
}