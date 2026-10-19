package com.example.servicopedido.messaging;

import com.example.servicopedido.event.PedidoEvent;

// porta de saida: o dominio avisa "aconteceu X" e pronto.
// Na camada spring isso vira um evento do Spring (ApplicationEventPublisher).
public interface PedidoEventPublisher {
    void publicar(PedidoEvent evento);
}
