package com.iceibank.agencia.mensageria;

import com.iceibank.agencia.config.AgenciaProperties;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "iceibank.eventos";

    public static String routingKeyCreditar(int idAgencia) {
        return "agencia." + idAgencia + ".creditar";
    }

    @Bean
    public TopicExchange exchangeEventos() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue filaCreditos(AgenciaProperties agencia) {
        return QueueBuilder.durable("fila-agencia-" + agencia.getIdAgencia()).build();
    }

    @Bean
    public Binding bindingCreditos(Queue filaCreditos, TopicExchange exchangeEventos, AgenciaProperties agencia) {
        return BindingBuilder.bind(filaCreditos)
                .to(exchangeEventos)
                .with(routingKeyCreditar(agencia.getIdAgencia()));
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
                    + ", routing key " + routingKeyCreditar(agencia.getIdAgencia()));
        };
    }
}