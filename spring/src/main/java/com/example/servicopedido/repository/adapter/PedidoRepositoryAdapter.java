package com.example.servicopedido.repository.adapter;

import java.util.List;

import com.example.servicopedido.entity.ItemPedido;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.repository.orm.ItemPedidoOrm;
import com.example.servicopedido.repository.orm.PedidoOrm;

public class PedidoRepositoryAdapter {

    private static final String SEPARADOR = "|";

    private PedidoRepositoryAdapter() {
    }

    public static Pedido castOrm(PedidoOrm orm) {
        List<ItemPedido> itens = orm.getItens().stream()
                .map(PedidoRepositoryAdapter::castOrm)
                .toList();

        return new Pedido(
                orm.getId(),
                orm.getClienteId(),
                orm.getStatus(),
                itens,
                orm.getTotal(),
                orm.getFormaPagamento(),
                orm.getObservacao(),
                orm.getSenhaRetirada(),
                orm.getChaveIdempotencia(),
                orm.getCriadoEm(),
                orm.getAtualizadoEm());
    }

    public static PedidoOrm castEntity(Pedido entity) {
        List<ItemPedidoOrm> itens = entity.itens().stream()
                .map(PedidoRepositoryAdapter::castEntity)
                .toList();

        return new PedidoOrm(
                entity.id(),
                entity.clienteId(),
                entity.status(),
                itens,
                entity.total(),
                entity.formaPagamento(),
                entity.observacao(),
                entity.senhaRetirada(),
                entity.chaveIdempotencia(),
                entity.criadoEm(),
                entity.atualizadoEm());
    }

    private static ItemPedido castOrm(ItemPedidoOrm orm) {
        return new ItemPedido(
                orm.getId(),
                orm.getProdutoId(),
                orm.getNomeProduto(),
                orm.getPrecoUnitario(),
                orm.getQuantidade(),
                separar(orm.getAdicionais()),
                orm.getValorAdicionais(),
                orm.getObservacao(),
                orm.getSubtotal());
    }

    private static ItemPedidoOrm castEntity(ItemPedido entity) {
        return new ItemPedidoOrm(
                entity.id(),
                entity.produtoId(),
                entity.nomeProduto(),
                entity.precoUnitario(),
                entity.quantidade(),
                String.join(SEPARADOR, entity.adicionais()),
                entity.valorAdicionais(),
                entity.observacao(),
                entity.subtotal());
    }

    private static List<String> separar(String adicionais) {
        if (adicionais == null || adicionais.isBlank()) {
            return List.of();
        }
        return List.of(adicionais.split("\\|"));
    }
}
