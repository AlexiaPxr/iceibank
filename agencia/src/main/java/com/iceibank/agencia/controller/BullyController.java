package com.iceibank.agencia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iceibank.agencia.dto.CoordenadorRequest;
import com.iceibank.agencia.dto.EleicaoRequest;
import com.iceibank.agencia.dto.LiderResponse;
import com.iceibank.agencia.service.BullyService;

/** Rotas do algoritmo de Bully, chamadas entre agencias (e consultadas pelo app). */
@RestController
@RequestMapping("/bully")
public class BullyController {

    private final BullyService bully;

    public BullyController(BullyService bully) {
        this.bully = bully;
    }

    @PostMapping("/eleicao")
    public ResponseEntity<Void> eleicao(@RequestBody EleicaoRequest corpo) {
        bully.receberEleicao(corpo.origemId(), corpo.vetorEnvio());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/coordenador")
    public ResponseEntity<Void> coordenador(@RequestBody CoordenadorRequest corpo) {
        bully.receberCoordenador(corpo.liderId(), corpo.vetorEnvio());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/lider")
    public LiderResponse lider() {
        return new LiderResponse(bully.getIdAgencia(), bully.obterLiderAtual());
    }
}