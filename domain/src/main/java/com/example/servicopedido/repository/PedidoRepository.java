package com.example.servicopedido.repository;

import java.util.List;
import java.util.Optional;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;

public interface PedidoRepository {
    Pedido salvar(Pedido pedido);

    Optional<Pedido> buscarPorId(Long id);

    // pedido que este cliente ja criou com esta Idempotency-Key (se houver)
    Optional<Pedido> buscarPorClienteEChave(Long clienteId, String chaveIdempotencia);

    // trava a linha do pedido ate o fim da transacao: evita, por exemplo,
    // o cliente cancelar no mesmo instante em que a cozinha comeca a preparar
    Optional<Pedido> buscarPorIdComLock(Long id);

    List<Pedido> listarTodos();

    List<Pedido> listarPorStatus(StatusPedido status);

    List<Pedido> listarPorCliente(Long clienteId);
}
