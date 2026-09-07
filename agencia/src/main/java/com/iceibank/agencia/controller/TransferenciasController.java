package com.iceibank.agencia.controller;

import com.iceibank.agencia.dto.CreditoRemotoRequest;
import com.iceibank.agencia.dto.TransferenciaRequest;
import com.iceibank.agencia.service.TransferenciaService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TransferenciasController {

    private final TransferenciaService transferenciaService;

    public TransferenciasController(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @PostMapping("/transferencias")
    public Map<String, String> transferir(@RequestBody TransferenciaRequest requisicao) {
        String mensagem = transferenciaService.transferir(
                requisicao.idOrigem(), requisicao.idDestino(), requisicao.valor()
        );
        return Map.of("mensagem", mensagem);
    }

    @PostMapping("/contas/{id}/creditar-remoto")
    public Map<String, Object> creditarRemoto(@PathVariable int id, @RequestBody CreditoRemotoRequest requisicao) {
        double saldoAtual = transferenciaService.creditarRemoto(
                id, requisicao.valor(), requisicao.timestampLamport(), requisicao.origemAgencia()
        );
        return Map.of("mensagem", "Crédito remoto aplicado.", "saldoAtual", saldoAtual);
    }
}
