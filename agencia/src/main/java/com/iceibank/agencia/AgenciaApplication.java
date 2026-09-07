package com.iceibank.agencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgenciaApplication {

    public static void main(String[] args) {
        // A identidade da agencia (0, 1 ou 2) e o OFFSET pessoal (para maquinas
        // compartilhadas de laboratorio) vêm de variaveis de ambiente, exatamente
        // como no roteiro original (AGENCIA_ID e OFFSET). A porta e calculada
        // ANTES de subir o contexto Spring, pois o Tomcat embutido precisa dela
        // já na inicializacao.
        int idAgencia = Integer.parseInt(System.getenv().getOrDefault("AGENCIA_ID", "0"));
        int offset = Integer.parseInt(System.getenv().getOrDefault("OFFSET", "0"));
        int porta = 4000 + offset + idAgencia;

        System.setProperty("server.port", String.valueOf(porta));
        System.setProperty("AGENCIA_ID", String.valueOf(idAgencia));
        System.setProperty("OFFSET", String.valueOf(offset));

        SpringApplication.run(AgenciaApplication.class, args);
    }
}
