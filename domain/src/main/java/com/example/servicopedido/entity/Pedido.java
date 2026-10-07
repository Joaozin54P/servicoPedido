package com.example.servicopedido.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record Pedido(
        Long id,
        String clienteEmail,
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
        return new Pedido(id, clienteEmail, novoStatus, itens, total, formaPagamento,
                observacao, senhaRetirada, chaveIdempotencia, criadoEm, LocalDateTime.now());
    }

    public Pedido comSenha(String senha) {
        return new Pedido(id, clienteEmail, status, itens, total, formaPagamento,
                observacao, senha, chaveIdempotencia, criadoEm, atualizadoEm);
    }
}
