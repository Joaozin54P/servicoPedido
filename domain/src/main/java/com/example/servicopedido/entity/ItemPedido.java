package com.example.servicopedido.entity;

import java.math.BigDecimal;
import java.util.List;

// "foto" do item no momento da compra: nome e preco ficam gravados no pedido,
// assim mudar o cardapio depois nao altera pedidos antigos
public record ItemPedido(
        Long id,
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
