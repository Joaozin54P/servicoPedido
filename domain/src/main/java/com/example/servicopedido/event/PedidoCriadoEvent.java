package com.example.servicopedido.event;

import java.time.LocalDateTime;
import java.util.List;

import com.example.servicopedido.entity.Pedido;

// vai para a cozinha: o que precisa ser preparado
public record PedidoCriadoEvent(
        Long pedidoId,
        String senhaRetirada,
        Long clienteId,
        String observacao,
        LocalDateTime criadoEm,
        List<Item> itens
) implements PedidoEvent {

    public record Item(
            String nomeProduto,
            int quantidade,
            List<String> adicionais,
            String observacao
    ) {
    }

    public static PedidoCriadoEvent de(Pedido pedido) {
        List<Item> itens = pedido.itens().stream()
                .map(i -> new Item(i.nomeProduto(), i.quantidade(), i.adicionais(), i.observacao()))
                .toList();

        return new PedidoCriadoEvent(pedido.id(), pedido.senhaRetirada(), pedido.clienteId(),
                pedido.observacao(), pedido.criadoEm(), itens);
    }
}
