package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.example.servicopedido.config.RabbitMQConfig;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.messaging.adapter.PedidoMessagingAdapter;

// Implementacao legada mantida como utilitario. A publicacao atual passa por
// PedidoEventPublisherSpring e o envio ao RabbitMQ por PedidoEventosRabbit.
public class PedidoEventPublisherRabbit {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisherRabbit.class);

    private final RabbitTemplate rabbitTemplate;

    public PedidoEventPublisherRabbit(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarPedidoCriado(PedidoCriadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CRIADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.criado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    public void publicarPedidoCancelado(PedidoCanceladoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CANCELADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.cancelado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    public void publicarPedidoFinalizado(PedidoFinalizadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_FINALIZADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }
}
