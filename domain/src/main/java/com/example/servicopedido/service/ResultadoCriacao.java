package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;

// repetido = true quando a mesma Idempotency-Key ja tinha gerado um pedido:
// devolvemos o pedido original em vez de criar outro (e de cobrar de novo)
public record ResultadoCriacao(
        Pedido pedido,
        boolean repetido
) {
}
