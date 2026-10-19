package com.example.servicopedido.messaging;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.example.servicopedido.event.PedidoEvent;

// Liga o dominio ao Spring: o dominio chama publicar(evento) e aqui o evento vira um evento do Spring.
// A partir dai, QUEM QUISER reagir so cria um @EventListener (ver PedidoEventosRabbit, PedidoEventosLog
// e PedidoStatusLog) - o servico de pedido nao chama ninguem diretamente.
@Component
public class PedidoEventPublisherSpring implements PedidoEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public PedidoEventPublisherSpring(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publicar(PedidoEvent evento) {
        applicationEventPublisher.publishEvent(evento);
    }
}
