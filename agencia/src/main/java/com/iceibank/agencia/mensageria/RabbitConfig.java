package com.iceibank.agencia.mensageria;

import com.iceibank.agencia.config.AgenciaProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia do RabbitMQ:
 *  - exchange topic "iceibank.eventos" e uma fila duravel por agencia
 *    ("fila-agencia-<id>"), ligada pela routing key "agencia.<id>.creditar";
 *  - (Sprint 2, funcionalidade adicional) dead-letter: a fila principal aponta
 *    para a exchange "iceibank.eventos.dlx"; mensagens rejeitadas sem requeue
 *    vao para "fila-agencia-<id>.dlq" em vez de serem descartadas.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "iceibank.eventos";
    public static final String DLX = "iceibank.eventos.dlx";

    public static String routingKeyCreditar(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    public static String routingKeyDlq(int idAgencia) {
        return "agencia." + idAgencia + ".creditar.dlq";
    }

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange exchangeMortas() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue filaCreditos(AgenciaProperties agencia) {
        return QueueBuilder.durable("fila-agencia-" + agencia.getIdAgencia())
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(routingKeyDlq(agencia.getIdAgencia()))
                .build();
    }

    @Bean
    public Queue filaCreditosDlq(AgenciaProperties agencia) {
        return QueueBuilder.durable("fila-agencia-" + agencia.getIdAgencia() + ".dlq").build();
    }

    @Bean
    public Binding bindingCreditos(Queue filaCreditos, TopicExchange exchangeEventos, AgenciaProperties agencia) {
        return BindingBuilder.bind(filaCreditos)
                .to(exchangeEventos)
                .with(routingKeyCreditar(agencia.getIdAgencia()));
    }

    @Bean
    public Binding bindingDlq(Queue filaCreditosDlq, DirectExchange exchangeMortas, AgenciaProperties agencia) {
        return BindingBuilder.bind(filaCreditosDlq)
                .to(exchangeMortas)
                .with(routingKeyDlq(agencia.getIdAgencia()));
    }

    @Bean
    public MessageConverter conversorJson() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public ApplicationRunner declararTopologia(AmqpAdmin amqpAdmin, AgenciaProperties agencia) {
        return args -> {
            amqpAdmin.initialize();
            System.out.println("[Agência " + agencia.getIdAgencia() + "] topologia RabbitMQ declarada: exchange "
                    + EXCHANGE + ", fila fila-agencia-" + agencia.getIdAgencia()
                    + " (dead-letter: fila-agencia-" + agencia.getIdAgencia() + ".dlq)"
                    + ", routing key " + routingKeyCreditar(agencia.getIdAgencia()));
        };
    }
}