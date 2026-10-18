package com.iceibank.agencia.mensageria;

import com.iceibank.agencia.service.TransferenciaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consome as mensagens da fila desta agencia. O nome da fila vem do bean
 * "filaCreditos" declarado em RabbitConfig (via SpEL), entao nao precisa
 * repetir o calculo de "fila-agencia-" + id aqui.
 *
 * Este metodo NAO passa pelo JwtAuthFilter - ele nao e uma rota HTTP, e sim
 * um listener interno do processo. Ver Parte C, pergunta 3, sobre o que isso
 * implica em termos de seguranca.
 */
@Component
public class ConsumidorCreditos {

    private final TransferenciaService transferenciaService;

    public ConsumidorCreditos(TransferenciaService transferenciaService) {
        this.transferenciaService = transferenciaService;
    }

    @RabbitListener(queues = "#{@filaCreditos.name}")
    public void receber(CreditoMensagem mensagem) {
        transferenciaService.aplicarCreditoRemoto(
                mensagem.idConta(), mensagem.valor(), mensagem.vetorEnvio(), mensagem.origemAgencia()
        );
    }
}