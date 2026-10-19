package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.exception.RegraDeNegocioException;

import jakarta.transaction.Transactional;

// So muda o status que a cozinha informa: EM_PREPARO ("comecei") e PRONTO ("ficou pronto").
public class AtualizarStatusCozinhaService {

    private final PedidoRegras regras;

    public AtualizarStatusCozinhaService(PedidoRegras regras) {
        this.regras = regras;
    }

    @Transactional
    public Pedido atualizar(Long pedidoId, StatusPedido novoStatus) {
        if (novoStatus != StatusPedido.EM_PREPARO && novoStatus != StatusPedido.PRONTO) {
            throw new RegraDeNegocioException("A cozinha so pode informar EM_PREPARO ou PRONTO");
        }

        Pedido pedido = regras.buscarComLock(pedidoId);
        return regras.mudarStatus(pedido, novoStatus);
    }
}
