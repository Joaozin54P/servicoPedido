package com.example.servicopedido.entity;

import java.math.BigDecimal;

public record AdicionalCardapio(
        Long id,
        String nome,
        BigDecimal preco,
        boolean disponivel
) {
}
