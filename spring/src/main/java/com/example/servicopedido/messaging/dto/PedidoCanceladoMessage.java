package com.example.servicopedido.messaging.dto;

// JSON da fila "pedido-cancelado" (servicoCozinha le)
public record PedidoCanceladoMessage(
        Long pedidoId
) {
}
