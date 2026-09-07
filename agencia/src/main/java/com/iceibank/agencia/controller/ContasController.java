package com.iceibank.agencia.controller;

import com.iceibank.agencia.dto.CriarContaRequest;
import com.iceibank.agencia.dto.ValorRequest;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.service.ContaService;
import com.iceibank.agencia.service.RegistroEventos;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contas")
public class ContasController {

    private final ContaService contaService;
    private final RegistroEventos registroEventos;

    public ContasController(ContaService contaService, RegistroEventos registroEventos) {
        this.contaService = contaService;
        this.registroEventos = registroEventos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conta criar(@RequestBody CriarContaRequest requisicao) {
        return contaService.criar(requisicao.id(), requisicao.nomeAluno(), requisicao.senha(), requisicao.saldoInicial());
    }

    @GetMapping("/{id}")
    public Conta consultar(@PathVariable int id) {
        return contaService.consultar(id);
    }

    @PostMapping("/{id}/depositar")
    public Conta depositar(@PathVariable int id, @RequestBody ValorRequest requisicao) {
        return contaService.depositar(id, requisicao.valor());
    }

    @PostMapping("/{id}/sacar")
    public Conta sacar(@PathVariable int id, @RequestBody ValorRequest requisicao) {
        return contaService.sacar(id, requisicao.valor());
    }

    /**
     * Funcionalidade adicional (secao 2.1 do roteiro): historico de
     * transacoes por conta - lista os eventos registrados para uma conta
     * especifica, lidos do log de eventos da agencia.
     */
    @GetMapping("/{id}/historico")
    public List<Map<String, Object>> historico(@PathVariable int id) {
        contaService.consultar(id); // valida que a conta existe (e pertence) nesta agencia
        return registroEventos.lerEventosDaConta(id);
    }
}
