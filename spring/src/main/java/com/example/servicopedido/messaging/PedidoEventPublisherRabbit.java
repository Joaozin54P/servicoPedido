package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.servicopedido.config.RabbitMQConfig;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.messaging.adapter.PedidoMessagingAdapter;

@ConditionalOnProperty(prefix = "app.mensageria", name = "habilitada", havingValue = "true")
@Component
public class PedidoEventPublisherRabbit implements PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisherRabbit.class);

    private final RabbitTemplate rabbitTemplate;

    public PedidoEventPublisherRabbit(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publicarPedidoCriado(PedidoCriadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CRIADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.criado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    @Override
    public void publicarPedidoCancelado(PedidoCanceladoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CANCELADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.cancelado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    @Override
    public void publicarPedidoFinalizado(PedidoFinalizadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_FINALIZADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }
}
