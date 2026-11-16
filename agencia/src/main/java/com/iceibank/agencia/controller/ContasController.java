package com.iceibank.agencia.controller;

import com.iceibank.agencia.dto.CriarContaRequest;
import com.iceibank.agencia.dto.SincronizarRequest;
import com.iceibank.agencia.dto.ValorRequest;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.service.CompararVetores;
import com.iceibank.agencia.service.ContaService;
import com.iceibank.agencia.service.RegistroEventos;
import com.iceibank.agencia.service.RelogioVetorial;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/contas")
public class ContasController {

    private final ContaService contaService;
    private final RegistroEventos registroEventos;
    private final RelogioVetorial relogio;

    public ContasController(ContaService contaService, RegistroEventos registroEventos, RelogioVetorial relogio) {
        this.contaService = contaService;
        this.registroEventos = registroEventos;
        this.relogio = relogio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Conta criar(@RequestBody CriarContaRequest requisicao) {
        return contaService.criar(requisicao.id(), requisicao.nomeAluno(), requisicao.senha(), requisicao.saldoInicial());
    }

    /** Parte E: alem da conta, devolve o vetor atual da agencia (o app guarda para sincronizar depois). */
    @GetMapping("/{id}")
    public Map<String, Object> consultar(@PathVariable int id) {
        Conta conta = contaService.consultar(id);
        return respostaConta(conta, relogio.valorAtual());
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
     * Parte E: sincroniza uma operacao feita offline. So aplica se o vetor
     * que o app conhecia for IGUAL ao vetor atual da agencia; senao 409.
     */
    @PostMapping("/{id}/sincronizar")
    public ResponseEntity<Map<String, Object>> sincronizar(@PathVariable int id,
                                                           @RequestBody SincronizarRequest req) {
        synchronized (contaService) {
            Conta conta = contaService.consultar(id); // 404 se nao existir
            if (req.vetorConhecido() == null) {
                return erro(HttpStatus.BAD_REQUEST, "vetorConhecido é obrigatório.");
            }
            int[] vetorAtual = relogio.valorAtual();
            String relacao;
            try {
                relacao = CompararVetores.comparar(req.vetorConhecido(), vetorAtual);
            } catch (IllegalArgumentException e) {
                return erro(HttpStatus.BAD_REQUEST, e.getMessage());
            }

            if (!"IGUAIS".equals(relacao)) {
                Map<String, Object> corpo = new LinkedHashMap<>();
                corpo.put("erro", "Conflito detectado: o estado da conta mudou desde a última sincronização do app.");
                corpo.put("vetorConhecido", req.vetorConhecido());
                corpo.put("vetorAtual", vetorAtual);
                corpo.put("contaAtual", conta);
                return ResponseEntity.status(HttpStatus.CONFLICT).body(corpo);
            }

            String tipo = req.tipo() == null ? "" : req.tipo();
            if ("DEPOSITO".equals(tipo)) {
                conta.setSaldo(conta.getSaldo() + req.valor());
            } else if ("SAQUE".equals(tipo)) {
                if (conta.getSaldo() < req.valor()) {
                    return erro(HttpStatus.BAD_REQUEST, "Saldo insuficiente.");
                }
                conta.setSaldo(conta.getSaldo() - req.valor());
            } else {
                return erro(HttpStatus.BAD_REQUEST, "Tipo de operação pendente não suportado.");
            }

            int[] vetor = relogio.eventoLocal();
            registroEventos.registrar("SINCRONIZACAO_OFFLINE", vetor,
                    Map.of("id", id, "tipo", tipo, "valor", req.valor()));
            return ResponseEntity.ok(respostaConta(conta, vetor));
        }
    }

    private static Map<String, Object> respostaConta(Conta conta, int[] vetor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", conta.getId());
        m.put("nomeAluno", conta.getNomeAluno());
        m.put("saldo", conta.getSaldo());
        m.put("vetorAtual", vetor);
        return m;
    }

    private static ResponseEntity<Map<String, Object>> erro(HttpStatus status, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("erro", msg);
        return ResponseEntity.status(status).body(m);
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
