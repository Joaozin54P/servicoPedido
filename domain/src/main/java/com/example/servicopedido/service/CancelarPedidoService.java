package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.messaging.PedidoEventPublisher;

import jakarta.transaction.Transactional;

// So cancela. Da para cancelar enquanto a cozinha ainda nao comecou (status CRIADO).
public class CancelarPedidoService {

    private final PedidoRegras regras;
    private final PedidoEventPublisher eventPublisher;

    public CancelarPedidoService(PedidoRegras regras, PedidoEventPublisher eventPublisher) {
        this.regras = regras;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Pedido cancelar(Long pedidoId, Long usuarioId, boolean funcionario) {
        Pedido pedido = regras.buscarComLock(pedidoId);
        regras.garantirAcesso(pedido, usuarioId, funcionario);

        Pedido cancelado = regras.mudarStatus(pedido, StatusPedido.CANCELADO);
        eventPublisher.publicar(new PedidoCanceladoEvent(cancelado.id()));
        return cancelado;
    }
}
