package com.example.servicopedido.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.example.servicopedido.entity.Pedido;

// vai para o relatorio: pedido retirado = venda concluida
public record PedidoFinalizadoEvent(
        Long pedidoId,
        Long clienteId,
        BigDecimal total,
        String formaPagamento,
        LocalDateTime finalizadoEm,
        List<Item> itens
) implements PedidoEvent {

    public record Item(
            Long produtoId,
            String nomeProduto,
            int quantidade,
            BigDecimal subtotal
    ) {
    }

    public static PedidoFinalizadoEvent de(Pedido pedido) {
        List<Item> itens = pedido.itens().stream()
                .map(i -> new Item(i.produtoId(), i.nomeProduto(), i.quantidade(), i.subtotal()))
                .toList();

        return new PedidoFinalizadoEvent(pedido.id(), pedido.clienteId(), pedido.total(),
                pedido.formaPagamento().name(), LocalDateTime.now(), itens);
    }
}
