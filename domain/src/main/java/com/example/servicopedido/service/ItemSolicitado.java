package com.example.servicopedido.service;

import java.util.List;

// o que o cliente pediu (antes de consultar preco no cardapio)
public record ItemSolicitado(
        Long produtoId,
        Integer quantidade,
        List<Long> adicionaisIds,
        String observacao
) {
}
