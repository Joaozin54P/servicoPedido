package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.servicopedido.event.PedidoStatusAlteradoEvent;

// Escuta TODA mudanca de status (criar/cozinha/retirar/cancelar passam todos pelo mesmo evento)
// e registra uma linha no log. Para reagir a mais coisas (historico, notificacao...) e so criar outro @EventListener.
@Component
public class PedidoStatusLog {

    private static final Logger log = LoggerFactory.getLogger(PedidoStatusLog.class);

    @EventListener
    public void aoMudarDeStatus(PedidoStatusAlteradoEvent evento) {
        log.info("Pedido #{}: {} -> {}", evento.pedidoId(), evento.statusAnterior(), evento.novoStatus());
    }
}
