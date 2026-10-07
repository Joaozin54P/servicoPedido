package com.example.servicopedido.controller.dto.request;

import java.util.List;

// {"formaPagamento":"PIX","observacao":"...","itens":[{"produtoId":1,"quantidade":2,"adicionaisIds":[1],"observacao":"sem cebola"}]}
public record CriarPedidoRequest(
        String formaPagamento,
        String observacao,
        List<ItemRequest> itens
) {
    public record ItemRequest(
            Long produtoId,
            Integer quantidade,
            List<Long> adicionaisIds,
            String observacao
    ) {
    }
}
