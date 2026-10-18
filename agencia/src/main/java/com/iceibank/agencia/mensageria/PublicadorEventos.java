package com.iceibank.agencia.mensageria;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Quem publica so conhece a exchange e a routing key de destino - nunca fala
 * diretamente com a outra agencia. E essa indirecao que da nome a unidade
 * "Comunicacao Indireta".
 */
@Component
public class PublicadorEventos {

    private final RabbitTemplate rabbitTemplate;

    public PublicadorEventos(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarCredito(int idAgenciaDestino, CreditoMensagem mensagem) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.routingKeyCreditar(idAgenciaDestino),
                mensagem
        );
    }
}