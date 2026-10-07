package com.example.servicopedido.controller.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PedidoResponse(
        Long id,
        String clienteEmail,
        String status,
        String senhaRetirada,
        String formaPagamento,
        String observacao,
        BigDecimal total,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        List<ItemResponse> itens
) {
    public record ItemResponse(
            Long produtoId,
            String nomeProduto,
            BigDecimal precoUnitario,
            Integer quantidade,
            List<String> adicionais,
            BigDecimal valorAdicionais,
            String observacao,
            BigDecimal subtotal
    ) {
    }
}
