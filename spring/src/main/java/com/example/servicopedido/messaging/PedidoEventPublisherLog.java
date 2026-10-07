package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;

// usado quando app.mensageria.habilitada=false (sem RabbitMQ): em vez de mandar a mensagem,
// mostra no console um "cupom" organizado (ver PedidoLogFormatter).
// Assim da para testar o servicoPedido sozinho, sem broker.
@Component
@ConditionalOnProperty(prefix = "app.mensageria", name = "habilitada", havingValue = "false", matchIfMissing = true)
public class PedidoEventPublisherLog implements PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisherLog.class);

    @Override
    public void publicarPedidoCriado(PedidoCriadoEvent evento) {
        log.info("\n{}", PedidoLogFormatter.criado(evento, PedidoLogFormatter.SO_NO_LOG));
    }

    @Override
    public void publicarPedidoCancelado(PedidoCanceladoEvent evento) {
        log.info("\n{}", PedidoLogFormatter.cancelado(evento, PedidoLogFormatter.SO_NO_LOG));
    }

    @Override
    public void publicarPedidoFinalizado(PedidoFinalizadoEvent evento) {
        log.info("\n{}", PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.SO_NO_LOG));
    }
}
