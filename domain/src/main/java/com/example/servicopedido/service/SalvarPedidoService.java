package com.example.servicopedido.service;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;

import jakarta.transaction.Transactional;

// So grava o pedido novo. A transacao e so em volta do "salvar" (e do aviso a cozinha):
// se o RabbitMQ falhar, o pedido nao fica gravado sem a cozinha saber.
// Fica separado do CriarPedidoService de proposito: o @Transactional so vale quando a chamada
// vem de OUTRA classe, e o CriarPedidoService precisa tratar a chave repetida FORA da transacao.
public class SalvarPedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoEventPublisher eventPublisher;

    public SalvarPedidoService(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher) {
        this.pedidoRepository = pedidoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Pedido salvarEPublicar(Pedido novo) {
        Pedido salvo = pedidoRepository.salvar(novo);
        Pedido comSenha = pedidoRepository.salvar(salvo.comSenha(String.format("%03d", salvo.id() % 1000)));

        eventPublisher.publicar(PedidoCriadoEvent.de(comSenha));
        return comSenha;
    }
}
