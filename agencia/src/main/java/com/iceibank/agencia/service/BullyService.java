package com.iceibank.agencia.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iceibank.agencia.config.AgenciaProperties;
import com.iceibank.agencia.config.AgenciaProperties.AgenciaInfo;

/**
 * Algoritmo de Bully (Parte A): elege sempre a agencia de MAIOR id entre as
 * que estao ativas. Equivale ao bullyService.js do roteiro.
 *
 * As chamadas entre agencias sao HTTP sincronas com timeout curto, porque a
 * eleicao precisa saber NA HORA se alguem maior respondeu (o que a mensageria
 * assincrona do Sprint 2 nao entrega).
 */
@Service
public class BullyService {

    private static final long TIMEOUT_MS = 1000;

    private final int idAgencia;
    private final AgenciaProperties agenciaProperties;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Eleicoes rodam em segundo plano: a rota /bully/eleicao precisa responder
    // 200 imediatamente (o "OK" implicito do algoritmo) e so depois eleger.
    private final ExecutorService executor = Executors.newCachedThreadPool(tarefa -> {
        Thread thread = new Thread(tarefa, "bully");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Integer liderAtual = null;
    private final AtomicBoolean eleicaoEmAndamento = new AtomicBoolean(false);

    public BullyService(AgenciaProperties agenciaProperties, RelogioVetorial relogio, RegistroEventos registro) {
        this.agenciaProperties = agenciaProperties;
        this.idAgencia = agenciaProperties.getIdAgencia();
        this.relogio = relogio;
        this.registro = registro;
    }

    /** Ao subir, cada agencia dispara sua propria eleicao para descobrir (ou estabelecer) a lider. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        iniciarEleicaoEmSegundoPlano();
    }

    public void iniciarEleicaoEmSegundoPlano() {
        executor.submit(this::iniciarEleicao);
    }

    private List<AgenciaInfo> agenciasComIdMaior() {
        return agenciaProperties.getAgencias().stream()
                .filter(a -> a.id() > idAgencia)
                .toList();
    }

    void iniciarEleicao() {
        if (!eleicaoEmAndamento.compareAndSet(false, true)) {
            return;
        }
        boolean alguemRespondeu = false;
        try {
            int[] vetor = relogio.eventoLocal();
            registro.registrar("ELEICAO_INICIADA", vetor, Map.of("idAgencia", idAgencia));

            for (AgenciaInfo agencia : agenciasComIdMaior()) {
                int[] vetorEnvio = relogio.aoEnviar();
                boolean respondeu = postar(agencia.url() + "/bully/eleicao",
                        Map.of("origemId", idAgencia, "vetorEnvio", vetorEnvio));
                if (respondeu) {
                    alguemRespondeu = true;
                }
                // se nao respondeu (fora do ar ou lenta), ignora e segue
            }
        } finally {
            eleicaoEmAndamento.set(false);
        }

        // Se alguem maior respondeu, e essa agencia quem conduz a propria
        // eleicao e anuncia o resultado via /bully/coordenador.
        if (!alguemRespondeu) {
            tornarMeLider();
        }
    }

    private void tornarMeLider() {
        liderAtual = idAgencia;
        int[] vetor = relogio.eventoLocal();
        registro.registrar("TORNOU_LIDER", vetor, Map.of("idAgencia", idAgencia));

        for (AgenciaInfo agencia : agenciaProperties.getAgencias()) {
            if (agencia.id() == idAgencia) {
                continue;
            }
            int[] vetorEnvio = relogio.aoEnviar();
            postar(agencia.url() + "/bully/coordenador",
                    Map.of("liderId", idAgencia, "vetorEnvio", vetorEnvio));
            // agencia fora do ar: vai descobrir a lider quando voltar
        }
    }

    /** Recebe uma eleicao de alguem com id menor: o 200 da rota e o "OK" e eu inicio a minha. */
    public void receberEleicao(int origemId, int[] vetorRecebido) {
        int[] vetor = relogio.aoReceber(vetorRecebido);
        registro.registrar("ELEICAO_RECEBIDA", vetor, Map.of("de", origemId, "minhaId", idAgencia));
        iniciarEleicaoEmSegundoPlano();
    }

    public void receberCoordenador(int liderId, int[] vetorRecebido) {
        liderAtual = liderId;
        int[] vetor = relogio.aoReceber(vetorRecebido);
        registro.registrar("LIDER_ANUNCIADO", vetor, Map.of("liderId", liderId, "agencia", idAgencia));
    }

    public Integer obterLiderAtual() {
        return liderAtual;
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    /** POST JSON com timeout; true so se a agencia respondeu com 2xx. */
    private boolean postar(String url, Map<String, Object> corpo) {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(corpo)))
                    .build();
            HttpResponse<Void> resposta = http.send(requisicao, HttpResponse.BodyHandlers.discarding());
            return resposta.statusCode() >= 200 && resposta.statusCode() < 300;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}