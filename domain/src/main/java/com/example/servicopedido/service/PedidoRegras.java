package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoStatusAlteradoEvent;
import com.example.servicopedido.exception.AcessoNegadoException;
import com.example.servicopedido.exception.NaoEncontradoException;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;

// regras que mais de um servico usa (quem pode ver o pedido, quem pode ir para qual status).
// Fica num lugar so para cada servico ficar pequeno e nao repetir codigo.
public class PedidoRegras {

    private final PedidoRepository pedidoRepository;
    private final PedidoEventPublisher eventPublisher;

    public PedidoRegras(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
    }

    // trava a linha do pedido (so funciona dentro de uma transacao: os servicos que chamam isso usam @Transactional)
    public Pedido buscarComLock(Long pedidoId) {
        return pedidoRepository.buscarPorIdComLock(pedidoId)
                .orElseThrow(() -> new NaoEncontradoException("Pedido nao encontrado"));
    }

    public void garantirAcesso(Pedido pedido, Long usuarioId, boolean funcionario) {
        if (!funcionario && !pedido.clienteId().equals(usuarioId)) {
            throw new AcessoNegadoException("Este pedido pertence a outro cliente");
        }
    }

    // valida a transicao, grava o novo status e avisa quem estiver escutando
    public Pedido mudarStatus(Pedido pedido, StatusPedido destino) {
        if (!pedido.status().podeIrPara(destino)) {
            throw new RegraDeNegocioException(
                    "O pedido " + pedido.id() + " esta " + pedido.status() + " e nao pode ir para " + destino);
        }

        Pedido atualizado = pedidoRepository.salvar(pedido.comStatus(destino));
        eventPublisher.publicar(new PedidoStatusAlteradoEvent(pedido.id(), pedido.status(), destino));
        return atualizado;
    }
}
