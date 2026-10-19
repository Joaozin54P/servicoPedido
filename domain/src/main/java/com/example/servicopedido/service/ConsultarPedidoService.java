package com.example.servicopedido.service;

import java.util.List;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.exception.NaoEncontradoException;
import com.example.servicopedido.repository.PedidoRepository;

// So consulta (nao altera nada).
public class ConsultarPedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoRegras regras;

    public ConsultarPedidoService(PedidoRepository pedidoRepository, PedidoRegras regras) {
        this.pedidoRepository = pedidoRepository;
        this.regras = regras;
    }

    public Pedido buscar(Long id, Long usuarioId, boolean funcionario) {
        Pedido pedido = pedidoRepository.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("Pedido nao encontrado"));
        regras.garantirAcesso(pedido, usuarioId, funcionario);
        return pedido;
    }

    public List<Pedido> listarDoCliente(Long clienteId) {
        return pedidoRepository.listarPorCliente(clienteId);
    }

    // painel da cozinha / admin. status == null lista todos
    public List<Pedido> listar(StatusPedido status) {
        return status == null
                ? pedidoRepository.listarTodos()
                : pedidoRepository.listarPorStatus(status);
    }
}
