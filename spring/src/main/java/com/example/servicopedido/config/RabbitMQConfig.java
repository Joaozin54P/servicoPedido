package com.example.servicopedido.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// nomes das filas = contrato com o servicoCozinha e o servicoRelatorio (ver README)
@ConditionalOnProperty(prefix = "app.mensageria", name = "habilitada", havingValue = "true")
@Configuration
public class RabbitMQConfig {

    // servicoPedido -> servicoCozinha
    public static final String FILA_PEDIDO_CRIADO = "pedido-criado";
    public static final String FILA_PEDIDO_CANCELADO = "pedido-cancelado";

    // servicoCozinha -> servicoPedido
    public static final String FILA_STATUS_COZINHA = "pedido-status-cozinha";

    // servicoPedido -> servicoRelatorio
    public static final String FILA_PEDIDO_FINALIZADO = "pedido-finalizado";

    @Bean
    public Queue filaPedidoCriado() {
        return new Queue(FILA_PEDIDO_CRIADO, true);
    }

    @Bean
    public Queue filaPedidoCancelado() {
        return new Queue(FILA_PEDIDO_CANCELADO, true);
    }

    @Bean
    public Queue filaStatusCozinha() {
        return new Queue(FILA_STATUS_COZINHA, true);
    }

    @Bean
    public Queue filaPedidoFinalizado() {
        return new Queue(FILA_PEDIDO_FINALIZADO, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
