package com.iceibank.agencia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iceibank.agencia.config.AgenciaProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Grava cada evento como uma linha JSON em agencia/data/eventos-agencia-N.jsonl.
 * E a materia-prima usada pelo script MesclarLogs (Parte E) e pela
 * funcionalidade adicional de historico por conta (secao 2.1).
 */
@Service
public class RegistroEventos {

    private final AgenciaProperties agenciaProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path caminhoArquivo;
    private String nomeAgencia;

    public RegistroEventos(AgenciaProperties agenciaProperties) {
        this.agenciaProperties = agenciaProperties;
    }

    @PostConstruct
    public void inicializar() throws IOException {
        this.nomeAgencia = "agencia-" + agenciaProperties.getIdAgencia();
        Path pastaDados = Path.of("data");
        Files.createDirectories(pastaDados);
        this.caminhoArquivo = pastaDados.resolve("eventos-" + nomeAgencia + ".jsonl");
    }

    public synchronized Map<String, Object> registrar(String tipo, int timestampLamport, Map<String, Object> detalhes) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("agencia", nomeAgencia);
        evento.put("tipo", tipo);
        evento.put("timestampLamport", timestampLamport);
        evento.put("horaParede", Instant.now().toString());
        evento.put("detalhes", detalhes);

        try {
            String linha = objectMapper.writeValueAsString(evento);
            try (FileWriter writer = new FileWriter(caminhoArquivo.toFile(), true)) {
                writer.write(linha + System.lineSeparator());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        System.out.println("[Lamport " + timestampLamport + "] " + tipo + " " + detalhes);
        return evento;
    }

    /**
     * Funcionalidade adicional (secao 2.1): historico de eventos de uma conta
     * especifica, usado pelo endpoint GET /contas/{id}/historico.
     */
    public List<Map<String, Object>> lerEventosDaConta(int idConta) {
        List<Map<String, Object>> resultado = new ArrayList<>();
        if (!Files.exists(caminhoArquivo)) {
            return resultado;
        }
        try {
            for (String linha : Files.readAllLines(caminhoArquivo)) {
                if (linha.isBlank()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> evento = objectMapper.readValue(linha, Map.class);
                Object detalhesObj = evento.get("detalhes");
                if (detalhesObj instanceof Map<?, ?> detalhes && pertenceAConta(detalhes, idConta)) {
                    resultado.add(evento);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return resultado;
    }

    private boolean pertenceAConta(Map<?, ?> detalhes, int idConta) {
        for (Object chave : List.of("id", "idConta", "idOrigem", "idDestino")) {
            Object valor = detalhes.get(chave);
            if (valor instanceof Number numero && numero.intValue() == idConta) {
                return true;
            }
        }
        return false;
    }
}
