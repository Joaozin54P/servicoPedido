package com.example.servicopedido.entity;

import java.math.BigDecimal;

// o que o servicoPedido precisa saber de um produto do servicoCardapio
public record ProdutoCardapio(
        Long id,
        String nome,
        BigDecimal preco,
        boolean disponivel
) {
}
