package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.messaging.PedidoEventPublisher;

import jakarta.transaction.Transactional;

// So retira: o cliente pegou o pedido no balcao e a venda esta concluida (vai para o relatorio).
public class RetirarPedidoService {

    private final PedidoRegras regras;
    private final PedidoEventPublisher eventPublisher;

    public RetirarPedidoService(PedidoRegras regras, PedidoEventPublisher eventPublisher) {
        this.regras = regras;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Pedido retirar(Long pedidoId) {
        Pedido pedido = regras.buscarComLock(pedidoId);

        Pedido retirado = regras.mudarStatus(pedido, StatusPedido.RETIRADO);
        eventPublisher.publicar(PedidoFinalizadoEvent.de(retirado));
        return retirado;
    }
}
