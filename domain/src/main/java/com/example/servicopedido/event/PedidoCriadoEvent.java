package com.example.servicopedido.event;

import java.time.LocalDateTime;
import java.util.List;

// vai para a cozinha: o que precisa ser preparado
public record PedidoCriadoEvent(
        Long pedidoId,
        String senhaRetirada,
        String clienteEmail,
        String observacao,
        LocalDateTime criadoEm,
        List<Item> itens
) {
    public record Item(
            String nomeProduto,
            int quantidade,
            List<String> adicionais,
            String observacao
    ) {
    }
}
