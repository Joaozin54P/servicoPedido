package com.example.servicopedido.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;

// JSON da fila "pedido-criado" (servicoCozinha le)
public record PedidoCriadoMessage(
        Long pedidoId,
        String senhaRetirada,
        Long clienteId,
        String observacao,
        LocalDateTime criadoEm,
        List<ItemMessage> itens
) {
    public record ItemMessage(
            String nomeProduto,
            int quantidade,
            List<String> adicionais,
            String observacao
    ) {
    }
}
