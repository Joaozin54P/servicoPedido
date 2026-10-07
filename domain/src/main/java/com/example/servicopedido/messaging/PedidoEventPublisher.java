package com.example.servicopedido.messaging;

import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;

// porta de saida: o dominio avisa "aconteceu X", a camada spring manda para o RabbitMQ
public interface PedidoEventPublisher {
    void publicarPedidoCriado(PedidoCriadoEvent evento);

    void publicarPedidoCancelado(PedidoCanceladoEvent evento);

    void publicarPedidoFinalizado(PedidoFinalizadoEvent evento);
}
