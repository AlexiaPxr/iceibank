package com.iceibank.agencia.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Equivalente ao agencia/src/config.js do roteiro original: guarda o
 * particionamento das contas entre as 3 agencias e a identidade desta
 * instancia (definida pela variavel de ambiente AGENCIA_ID).
 */
@Component
public class AgenciaProperties {

    public static final int NUMERO_AGENCIAS = 3;

    @Value("${AGENCIA_ID:0}")
    private int idAgencia;

    @Value("${OFFSET:0}")
    private int offset;

    private List<AgenciaInfo> agencias;

    @PostConstruct
    public void inicializar() {
        int portaBase = 4000 + offset;
        agencias = List.of(
                new AgenciaInfo(0, "http://localhost:" + portaBase),
                new AgenciaInfo(1, "http://localhost:" + (portaBase + 1)),
                new AgenciaInfo(2, "http://localhost:" + (portaBase + 2))
        );
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    public int getOffset() {
        return offset;
    }

    public List<AgenciaInfo> getAgencias() {
        return agencias;
    }

    /** Regra de particionamento: id_conta % numero_de_agencias. */
    public int agenciaResponsavel(int idConta) {
        return Math.floorMod(idConta, NUMERO_AGENCIAS);
    }

    public String urlDaAgencia(int idAgenciaAlvo) {
        return agencias.stream()
                .filter(a -> a.id() == idAgenciaAlvo)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agência não configurada: " + idAgenciaAlvo))
                .url();
    }

    public record AgenciaInfo(int id, String url) {
    }
}
