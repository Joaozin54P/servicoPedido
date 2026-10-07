package com.example.servicopedido.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// vai para o relatorio: pedido retirado = venda concluida
public record PedidoFinalizadoEvent(
        Long pedidoId,
        String clienteEmail,
        BigDecimal total,
        String formaPagamento,
        LocalDateTime finalizadoEm,
        List<Item> itens
) {
    public record Item(
            Long produtoId,
            String nomeProduto,
            int quantidade,
            BigDecimal subtotal
    ) {
    }
}
