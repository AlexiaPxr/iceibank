package com.iceibank.agencia.tools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Script standalone (Parte E) que le os .jsonl de todas as agencias e monta
 * uma unica linha do tempo, ordenada por relogio de Lamport.
 *
 * Como executar (a partir da pasta agencia/, depois de ja ter gerado eventos
 * rodando as agencias e fazendo algumas operacoes):
 *
 *   Opcao 1 - pela IDE: clique com o botao direito neste arquivo -> Run 'MesclarLogs.main()'
 *
 *   Opcao 2 - pelo terminal, com Maven:
 *     mvn compile exec:java -Dexec.mainClass="com.iceibank.agencia.tools.MesclarLogs"
 *
 * O script le a pasta "data" relativa ao diretorio de onde ele e executado -
 * rode-o sempre a partir da pasta agencia/ (mesma pasta onde as agencias
 * geram seus arquivos data/eventos-agencia-N.jsonl).
 */
public class MesclarLogs {

    public static void main(String[] args) throws IOException {
        Path pastaDados = Path.of("data");
        if (!Files.isDirectory(pastaDados)) {
            System.out.println("Pasta 'data' não encontrada em " + pastaDados.toAbsolutePath());
            System.out.println("Rode as agências, gere alguns eventos, e execute este script a partir da pasta agencia/.");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> todosEventos = new ArrayList<>();

        try (DirectoryStream<Path> arquivos = Files.newDirectoryStream(pastaDados, "*.jsonl")) {
            for (Path arquivo : arquivos) {
                for (String linha : Files.readAllLines(arquivo)) {
                    if (linha.isBlank()) continue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> evento = mapper.readValue(linha, Map.class);
                    todosEventos.add(evento);
                }
            }
        }

        if (todosEventos.isEmpty()) {
            System.out.println("Nenhum evento encontrado em " + pastaDados.toAbsolutePath());
            return;
        }

        todosEventos.sort((a, b) -> {
            int tsA = ((Number) a.get("timestampLamport")).intValue();
            int tsB = ((Number) b.get("timestampLamport")).intValue();
            return Integer.compare(tsA, tsB);
        });

        System.out.println("=== Linha do tempo unificada (ordenada por relógio de Lamport) ===");
        for (Map<String, Object> evento : todosEventos) {
            System.out.printf(
                    "[Lamport %s] (%s) %s - %s %s%n",
                    evento.get("timestampLamport"),
                    evento.get("horaParede"),
                    evento.get("agencia"),
                    evento.get("tipo"),
                    evento.get("detalhes")
            );
        }
    }
}
