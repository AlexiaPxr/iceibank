package com.iceibank.agencia.service;

import com.iceibank.agencia.config.AgenciaProperties;
import com.iceibank.agencia.dto.CreditoRemotoRequest;
import com.iceibank.agencia.exception.ContaNaoEncontradaException;
import com.iceibank.agencia.exception.FalhaTransferenciaRemotaException;
import com.iceibank.agencia.exception.SaldoInsuficienteException;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.security.JwtUtil;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TransferenciaService {

    private final ContaService contaService;
    private final AgenciaProperties agenciaProperties;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    public TransferenciaService(ContaService contaService, AgenciaProperties agenciaProperties,
                                 RelogioVetorial relogio, RegistroEventos registro, JwtUtil jwtUtil) {
        this.contaService = contaService;
        this.agenciaProperties = agenciaProperties;
        this.relogio = relogio;
        this.registro = registro;
        this.jwtUtil = jwtUtil;
    }

    public synchronized String transferir(int idOrigem, int idDestino, double valor) {
        Conta contaOrigem = contaService.consultar(idOrigem);
        if (contaOrigem.getSaldo() < valor) {
            throw new SaldoInsuficienteException("Saldo insuficiente.");
        }

        int agenciaDestino = agenciaProperties.agenciaResponsavel(idDestino);

        int[] vetorDebito = relogio.eventoLocal();
        contaOrigem.setSaldo(contaOrigem.getSaldo() - valor);
        registro.registrar("TRANSFERENCIA_DEBITO", vetorDebito, Map.of(
                "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor
        ));

        if (agenciaDestino == agenciaProperties.getIdAgencia()) {
            return transferirMesmaAgencia(contaOrigem, idOrigem, idDestino, valor);
        }
        return transferirEntreAgencias(idOrigem, idDestino, valor, agenciaDestino);
    }

    private String transferirMesmaAgencia(Conta contaOrigem, int idOrigem, int idDestino, double valor) {
        try {
            Conta contaDestino = contaService.consultar(idDestino);
            int[] vetorCredito = relogio.eventoLocal();
            contaDestino.setSaldo(contaDestino.getSaldo() + valor);
            registro.registrar("TRANSFERENCIA_CREDITO", vetorCredito, Map.of(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor
            ));
            return "Transferência concluída (mesma agência).";
        } catch (ContaNaoEncontradaException e) {
            contaOrigem.setSaldo(contaOrigem.getSaldo() + valor); // reverte o debito
            throw e;
        }
    }

    private String transferirEntreAgencias(int idOrigem, int idDestino, double valor, int agenciaDestino) {
        int[] vetorEnvio = relogio.aoEnviar();
        String urlDestino = agenciaProperties.urlDaAgencia(agenciaDestino);
        String tokenInterno = jwtUtil.gerarTokenInterno(agenciaProperties.getIdAgencia());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(tokenInterno);
            headers.setContentType(MediaType.APPLICATION_JSON);

            CreditoRemotoRequest corpo = new CreditoRemotoRequest(valor, vetorEnvio, agenciaProperties.getIdAgencia());
            HttpEntity<CreditoRemotoRequest> requisicao = new HttpEntity<>(corpo, headers);

            restTemplate.postForEntity(urlDestino + "/contas/" + idDestino + "/creditar-remoto", requisicao, Map.class);
            return "Transferência concluída (entre agências).";
        } catch (RestClientException e) {

            registro.registrar("TRANSFERENCIA_FALHOU", relogio.eventoLocal(), Map.of(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "erro", String.valueOf(e.getMessage())
            ));
            throw new FalhaTransferenciaRemotaException(
                    "Falha ao contatar agência de destino. Débito já aplicado - inconsistência conhecida (ver Sprint 4)."
            );
        }
    }

    public synchronized double creditarRemoto(int idConta, double valor, int[] vetorEnvio, int origemAgencia) {

        int[] vetor = relogio.aoReceber(vetorEnvio);

        Conta conta = contaService.consultar(idConta);
        conta.setSaldo(conta.getSaldo() + valor);

        registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", vetor, Map.of(
                "idConta", idConta, "valor", valor, "origemAgencia", origemAgencia
        ));

        return conta.getSaldo();
    }
}