package com.example.servicopedido.controller.adapter;

import java.util.List;

import com.example.servicopedido.controller.dto.request.CriarPedidoRequest;
import com.example.servicopedido.controller.dto.response.PedidoResponse;
import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.ItemPedido;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.service.ItemSolicitado;

public class PedidoControllerAdapter {

    private PedidoControllerAdapter() {
    }

    public static FormaPagamento castFormaPagamento(String formaPagamento) {
        try {
            return FormaPagamento.valueOf(formaPagamento == null ? "" : formaPagamento.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Forma de pagamento invalida. Use PIX, CREDITO, DEBITO ou DINHEIRO");
        }
    }

    public static StatusPedido castStatus(String status) {
        try {
            return StatusPedido.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RegraDeNegocioException("Status invalido. A cozinha usa EM_PREPARO ou PRONTO");
        }
    }

    public static List<ItemSolicitado> castItens(CriarPedidoRequest request) {
        if (request.itens() == null) {
            return List.of();
        }
        return request.itens().stream()
                .map(i -> new ItemSolicitado(i.produtoId(), i.quantidade(), i.adicionaisIds(), i.observacao()))
                .toList();
    }

    public static PedidoResponse castResponse(Pedido pedido) {
        List<PedidoResponse.ItemResponse> itens = pedido.itens().stream()
                .map(PedidoControllerAdapter::castResponse)
                .toList();

        return new PedidoResponse(
                pedido.id(),
                pedido.clienteEmail(),
                pedido.status().name(),
                pedido.senhaRetirada(),
                pedido.formaPagamento().name(),
                pedido.observacao(),
                pedido.total(),
                pedido.criadoEm(),
                pedido.atualizadoEm(),
                itens);
    }

    private static PedidoResponse.ItemResponse castResponse(ItemPedido item) {
        return new PedidoResponse.ItemResponse(
                item.produtoId(),
                item.nomeProduto(),
                item.precoUnitario(),
                item.quantidade(),
                item.adicionais(),
                item.valorAdicionais(),
                item.observacao(),
                item.subtotal());
    }
}
