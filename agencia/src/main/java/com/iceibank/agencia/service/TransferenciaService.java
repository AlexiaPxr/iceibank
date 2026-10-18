package com.iceibank.agencia.service;

import com.iceibank.agencia.config.AgenciaProperties;
import com.iceibank.agencia.exception.ContaNaoEncontradaException;
import com.iceibank.agencia.exception.FalhaTransferenciaRemotaException;
import com.iceibank.agencia.exception.SaldoInsuficienteException;
import com.iceibank.agencia.mensageria.CreditoMensagem;
import com.iceibank.agencia.mensageria.PublicadorEventos;
import com.iceibank.agencia.model.Conta;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Map;

@Service
public class TransferenciaService {

    private final ContaService contaService;
    private final AgenciaProperties agenciaProperties;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final PublicadorEventos publicador;

    public TransferenciaService(ContaService contaService, AgenciaProperties agenciaProperties,
                                 RelogioVetorial relogio, RegistroEventos registro, PublicadorEventos publicador) {
        this.contaService = contaService;
        this.agenciaProperties = agenciaProperties;
        this.relogio = relogio;
        this.registro = registro;
        this.publicador = publicador;
    }

    public synchronized String transferir(int idOrigem, int idDestino, double valor) {
        Conta contaOrigem = contaService.consultar(idOrigem);
        if (contaOrigem.getSaldo() < valor) {
            throw new SaldoInsuficienteException("Saldo insuficiente.");
        }

        int agenciaDestino = agenciaProperties.agenciaResponsavel(idDestino);

        // O debito e sempre local, pois esta agencia e a dona da conta de origem.
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

    /**
     * Substitui a chamada REST direta do Sprint 1: em vez de esperar a outra
     * agencia responder na hora, publicamos um evento na exchange e seguimos.
     * A resposta ao cliente muda de sentido - 200 aqui significa "a mensagem
     * foi publicada", nao mais "o credito ja foi aplicado do outro lado".
     */
    private String transferirEntreAgencias(int idOrigem, int idDestino, double valor, int agenciaDestino) {
        int[] vetorEnvio = relogio.aoEnviar();
        CreditoMensagem mensagem = new CreditoMensagem(idDestino, valor, vetorEnvio, agenciaProperties.getIdAgencia());

        try {
            publicador.publicarCredito(agenciaDestino, mensagem);
        } catch (AmqpException e) {
            // Diferente do Sprint 1: aqui a falha e no broker (RabbitMQ
            // inacessivel), nao na agencia de destino - o debito tambem nao e
            // revertido, pela mesma razao de sempre (fora do escopo deste sprint).
            registro.registrar("TRANSFERENCIA_PUBLICACAO_FALHOU", relogio.eventoLocal(), Map.of(
                    "idOrigem", idOrigem, "idDestino", idDestino, "valor", valor,
                    "erro", String.valueOf(e.getMessage())
            ));
            throw new FalhaTransferenciaRemotaException(
                    "Falha ao publicar mensagem no RabbitMQ. Débito já aplicado - broker inacessível."
            );
        }

        return "Transferência publicada para a agência de destino (entrega assíncrona).";
    }

        /**
     * Chamado pelo consumidor da fila. Se a conta nao existe, registra o evento
     * (com o vetor, regra 3) e REJEITA a mensagem sem recoloca-la na fila: o
     * RabbitMQ a encaminha para a dead-letter queue desta agencia, em vez de a
     * mensagem ser descartada com ack como no inicio do Sprint 2.
     */
    public synchronized void aplicarCreditoRemoto(int idConta, double valor, int[] vetorEnvio, int origemAgencia) {
        int[] vetor = relogio.aoReceber(vetorEnvio);

        Conta conta = contaService.buscar(idConta);
        if (conta == null) {
            registro.registrar("CREDITO_REMOTO_FALHOU", vetor, Map.of(
                    "idConta", idConta, "valor", valor, "origemAgencia", origemAgencia,
                    "motivo", "conta nao encontrada", "destino", "dead-letter queue"
            ));
            throw new AmqpRejectAndDontRequeueException("Conta " + idConta + " não encontrada nesta agência.");
        }

        conta.setSaldo(conta.getSaldo() + valor);
        registro.registrar("TRANSFERENCIA_CREDITO_REMOTO", vetor, Map.of(
                "idConta", idConta, "valor", valor, "origemAgencia", origemAgencia
        ));
    }
}