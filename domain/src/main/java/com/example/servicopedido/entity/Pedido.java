package com.example.servicopedido.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Pedido(
        Long id,
        Long clienteId,
        StatusPedido status,
        List<ItemPedido> itens,
        BigDecimal total,
        FormaPagamento formaPagamento,
        String observacao,
        String senhaRetirada,
        String chaveIdempotencia,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm
) {

    public Pedido comStatus(StatusPedido novoStatus) {
        return new Pedido(id, clienteId, novoStatus, itens, total, formaPagamento,
                observacao, senhaRetirada, chaveIdempotencia, criadoEm, LocalDateTime.now());
    }

    public Pedido comSenha(String senha) {
        return new Pedido(id, clienteId, status, itens, total, formaPagamento,
                observacao, senha, chaveIdempotencia, criadoEm, atualizadoEm);
    }
}
