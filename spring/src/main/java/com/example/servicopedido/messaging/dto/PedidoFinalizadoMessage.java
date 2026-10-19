package com.example.servicopedido.messaging.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// JSON da fila "pedido-finalizado" (servicoRelatorio le)
public record PedidoFinalizadoMessage(
        Long pedidoId,
        Long clienteId,
        BigDecimal total,
        String formaPagamento,
        LocalDateTime finalizadoEm,
        List<ItemMessage> itens
) {
    public record ItemMessage(
            Long produtoId,
            String nomeProduto,
            int quantidade,
            BigDecimal subtotal
    ) {
    }
}
