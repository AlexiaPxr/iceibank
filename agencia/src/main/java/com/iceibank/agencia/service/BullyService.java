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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private static final long INTERVALO_HEARTBEAT_MS = 3000;
    // Quantas verificacoes SEGUIDAS precisam falhar antes de declarar a lider morta.
    // Com 1, uma unica resposta lenta (maquina sobrecarregada) ja derrubava a lider.
    private static final int FALHAS_PARA_DECLARAR_MORTA = 2;

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

    // Heartbeat (Parte B): uma thread que, de tempos em tempos, confere se a lider
    // conhecida ainda responde. scheduleWithFixedDelay evita verificacoes
    // sobrepostas se uma demorar (ex.: lider fora do ar = espera o timeout).
    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(tarefa -> {
        Thread thread = new Thread(tarefa, "bully-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private volatile Integer liderAtual = null;
    private volatile int falhasConsecutivas = 0; // so a thread do heartbeat incrementa
    private final AtomicBoolean eleicaoEmAndamento = new AtomicBoolean(false);

    public BullyService(AgenciaProperties agenciaProperties, RelogioVetorial relogio, RegistroEventos registro) {
        this.agenciaProperties = agenciaProperties;
        this.idAgencia = agenciaProperties.getIdAgencia();
        this.relogio = relogio;
        this.registro = registro;
    }

    /**
     * Ao subir, cada agencia dispara sua propria eleicao para descobrir (ou
     * estabelecer) a lider e passa a monitora-la com o heartbeat.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        iniciarEleicaoEmSegundoPlano();
        iniciarHeartbeat(INTERVALO_HEARTBEAT_MS);
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
        int[] vetor = relogio.aoReceber(vetorRecebido);

        // Regra do Bully: so aceito como lider quem tem id MAIOR (ou igual) ao meu.
        // Se um id menor se declarou lider, eu estou vivo e tenho mais direito:
        // ignoro o anuncio e conduzo minha propria eleicao.
        if (liderId < idAgencia) {
            registro.registrar("COORDENADOR_IGNORADO", vetor, Map.of("liderId", liderId, "agencia", idAgencia));
            iniciarEleicaoEmSegundoPlano();
            return;
        }

        liderAtual = liderId;
        falhasConsecutivas = 0;
        registro.registrar("LIDER_ANUNCIADO", vetor, Map.of("liderId", liderId, "agencia", idAgencia));
    }

    /** Inicia a verificacao periodica da lider (Parte B). */
    public void iniciarHeartbeat(long intervaloMs) {
        agendador.scheduleWithFixedDelay(this::verificarLiderSeguro, intervaloMs, intervaloMs,
                TimeUnit.MILLISECONDS);
    }

    // Uma excecao nao tratada cancelaria TODAS as execucoes futuras do agendador.
    private void verificarLiderSeguro() {
        try {
            verificarLider();
        } catch (RuntimeException e) {
            System.err.println("[Bully] erro no heartbeat: " + e);
        }
    }

    void verificarLider() {
        Integer lider = liderAtual;
        if (lider == null) {
            iniciarEleicao();
            return;
        }
        if (lider == idAgencia) {
            falhasConsecutivas = 0;
            return; // eu sou a lider, nada a verificar
        }

        if (consultar(agenciaProperties.urlDaAgencia(lider) + "/bully/lider")) {
            falhasConsecutivas = 0;
            return;
        }

        falhasConsecutivas++;
        if (falhasConsecutivas < FALHAS_PARA_DECLARAR_MORTA) {
            return; // pode ser so uma resposta lenta: tenta de novo no proximo ciclo
        }

        falhasConsecutivas = 0;
        int[] vetor = relogio.eventoLocal();
        registro.registrar("LIDER_NAO_RESPONDEU", vetor, Map.of("liderAtual", lider, "agencia", idAgencia));
        liderAtual = null;
        iniciarEleicao();
    }

    public Integer obterLiderAtual() {
        return liderAtual;
    }

    public int getIdAgencia() {
        return idAgencia;
    }

    /** GET com timeout; true so se a agencia respondeu com 2xx. */
    private boolean consultar(String url) {
        try {
            HttpRequest requisicao = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET()
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