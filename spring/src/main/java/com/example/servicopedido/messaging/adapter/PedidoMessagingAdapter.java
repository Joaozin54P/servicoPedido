package com.example.servicopedido.messaging.adapter;

import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.messaging.dto.PedidoCanceladoMessage;
import com.example.servicopedido.messaging.dto.PedidoCriadoMessage;
import com.example.servicopedido.messaging.dto.PedidoFinalizadoMessage;

public class PedidoMessagingAdapter {

    private PedidoMessagingAdapter() {
    }

    public static PedidoCriadoMessage castEvent(PedidoCriadoEvent evento) {
        return new PedidoCriadoMessage(
                evento.pedidoId(),
                evento.senhaRetirada(),
                evento.clienteId(),
                evento.observacao(),
                evento.criadoEm(),
                evento.itens().stream()
                        .map(i -> new PedidoCriadoMessage.ItemMessage(
                                i.nomeProduto(), i.quantidade(), i.adicionais(), i.observacao()))
                        .toList());
    }

    public static PedidoCanceladoMessage castEvent(PedidoCanceladoEvent evento) {
        return new PedidoCanceladoMessage(evento.pedidoId());
    }

    public static PedidoFinalizadoMessage castEvent(PedidoFinalizadoEvent evento) {
        return new PedidoFinalizadoMessage(
                evento.pedidoId(),
                evento.clienteId(),
                evento.total(),
                evento.formaPagamento(),
                evento.finalizadoEm(),
                evento.itens().stream()
                        .map(i -> new PedidoFinalizadoMessage.ItemMessage(
                                i.produtoId(), i.nomeProduto(), i.quantidade(), i.subtotal()))
                        .toList());
    }
}
