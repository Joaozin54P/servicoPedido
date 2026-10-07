package com.example.servicopedido.messaging.dto;

// JSON da fila "pedido-status-cozinha" (o servicoCozinha PUBLICA, o servicoPedido le)
// status: "EM_PREPARO" ou "PRONTO"
public record StatusCozinhaMessage(
        Long pedidoId,
        String status
) {
}
