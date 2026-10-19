package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.servicopedido.config.RabbitMQConfig;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.messaging.adapter.PedidoMessagingAdapter;

// Escuta os eventos do pedido e manda para as filas do RabbitMQ (app.mensageria.habilitada=true).
// O @EventListener roda na mesma thread e na mesma transacao de quem publicou: se o RabbitMQ
// falhar, o erro volta e a transacao do pedido sofre rollback (nao fica pedido gravado sem a cozinha saber).
@ConditionalOnProperty(prefix = "app.mensageria", name = "habilitada", havingValue = "true")
@Component
public class PedidoEventosRabbit {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventosRabbit.class);

    private final RabbitTemplate rabbitTemplate;

    public PedidoEventosRabbit(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @EventListener
    public void aoCriar(PedidoCriadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CRIADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.criado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    @EventListener
    public void aoCancelar(PedidoCanceladoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_CANCELADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.cancelado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }

    @EventListener
    public void aoFinalizar(PedidoFinalizadoEvent evento) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.FILA_PEDIDO_FINALIZADO, PedidoMessagingAdapter.castEvent(evento));
        log.info("\n{}", PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ));
    }
}
