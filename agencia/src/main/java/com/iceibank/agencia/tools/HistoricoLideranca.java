package com.iceibank.agencia.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Parte C: reconstroi a linha do tempo de liderancas a partir dos logs das 3 agencias.
 * Equivale ao historico-lideranca.js do roteiro.
 *
 * Uso (dentro da pasta agencia):
 *   mvn -q compile exec:java -Dexec.mainClass="com.iceibank.agencia.tools.HistoricoLideranca"
 * Opcional: -Dexec.args="outra/pasta/de/logs"
 */
public class HistoricoLideranca {

    private static final Set<String> TIPOS_BULLY = Set.of(
            "ELEICAO_INICIADA", "ELEICAO_RECEBIDA", "TORNOU_LIDER",
            "LIDER_ANUNCIADO", "LIDER_NAO_RESPONDEU", "COORDENADOR_IGNORADO");

    private static final DateTimeFormatter HORA_LOCAL =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private record Evento(Instant hora, String horaTexto, String agencia, String tipo, JsonNode detalhes) {}

    public static void main(String[] args) throws IOException {
        Path pasta = Path.of(args.length > 0 ? args[0] : "data");
        if (!Files.isDirectory(pasta)) {
            System.err.println("Pasta de logs nao encontrada: " + pasta.toAbsolutePath()
                    + " (rode de dentro da pasta agencia)");
            return;
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Evento> eventos = new ArrayList<>();

        List<Path> arquivos;
        try (Stream<Path> lista = Files.list(pasta)) {
            arquivos = lista
                    .filter(p -> p.getFileName().toString().startsWith("eventos-agencia-"))
                    .filter(p -> p.getFileName().toString().endsWith(".jsonl"))
                    .sorted()
                    .toList();
        }

        for (Path arquivo : arquivos) {
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                if (linha.isBlank()) {
                    continue;
                }
                try {
                    JsonNode no = mapper.readTree(linha);
                    String tipo = no.path("tipo").asText();
                    if (!TIPOS_BULLY.contains(tipo)) {
                        continue;
                    }
                    String horaTexto = no.path("horaParede").asText();
                    eventos.add(new Evento(Instant.parse(horaTexto), horaTexto,
                            no.path("agencia").asText(), tipo, no.path("detalhes")));
                } catch (Exception e) {
                    System.err.println("Linha ignorada em " + arquivo.getFileName() + ": " + e.getMessage());
                }
            }
        }

        // Ordena pela hora de parede (UTC). Instant compara certo mesmo quando
        // as fracoes de segundo tem tamanhos diferentes.
        eventos.sort(Comparator.comparing(Evento::hora));

        System.out.println("=== Historico de eventos de eleicao ===");
        for (Evento e : eventos) {
            System.out.println("(" + e.horaTexto() + " | " + HORA_LOCAL.format(e.hora()) + " local) ["
                    + e.agencia() + "] " + e.tipo() + " " + e.detalhes());
        }

        System.out.println();
        System.out.println("=== Linha do tempo de lideres ===");
        int eleicoes = 0;
        int declaracoes = 0;
        int mudancas = 0;
        int ultimoLider = -1;
        for (Evento e : eventos) {
            if (e.tipo().equals("ELEICAO_INICIADA")) {
                eleicoes++;
            }
            if (!e.tipo().equals("TORNOU_LIDER")) {
                continue;
            }
            declaracoes++;
            int id = e.detalhes().path("idAgencia").asInt(-1);
            System.out.println("A partir de " + e.horaTexto() + " (" + HORA_LOCAL.format(e.hora())
                    + " local): Agencia " + id + " e a lider");
            if (ultimoLider != -1 && id != ultimoLider) {
                mudancas++;
            }
            ultimoLider = id;
        }

        System.out.println();
        System.out.println("=== Resumo ===");
        System.out.println("Eleicoes iniciadas (ELEICAO_INICIADA): " + eleicoes);
        System.out.println("Declaracoes de lideranca (TORNOU_LIDER): " + declaracoes);
        System.out.println("Trocas de lider (lider diferente da anterior): " + mudancas);
        if (ultimoLider != -1) {
            System.out.println("Ultima lider declarada: Agencia " + ultimoLider);
        }
    }
}
