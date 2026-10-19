package com.example.servicopedido.event;

import com.example.servicopedido.entity.StatusPedido;

// disparado em TODA mudanca de status (CRIADO -> EM_PREPARO -> PRONTO -> RETIRADO, ou CANCELADO).
// Quem quiser reagir (log, historico, notificacao...) so escuta; ninguem precisa ser chamado na mao.
public record PedidoStatusAlteradoEvent(
        Long pedidoId,
        StatusPedido statusAnterior,
        StatusPedido novoStatus
) implements PedidoEvent {
}
