package com.example.servicopedido.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.exception.ChaveDuplicadaException;
import com.example.servicopedido.repository.adapter.PedidoRepositoryAdapter;
import com.example.servicopedido.repository.jpa.PedidoRepositoryWithJpa;
import com.example.servicopedido.repository.orm.PedidoOrm;

@Repository
public class PedidoRepositoryImpl implements PedidoRepository {

    private final PedidoRepositoryWithJpa repository;

    public PedidoRepositoryImpl(PedidoRepositoryWithJpa repository) {
        this.repository = repository;
    }

    @Override
    public Pedido salvar(Pedido pedido) {
        PedidoOrm orm = PedidoRepositoryAdapter.castEntity(pedido);
        try {
            // saveAndFlush: grava na hora, assim a restricao unica reclama aqui (e nao so no commit)
            return PedidoRepositoryAdapter.castOrm(repository.saveAndFlush(orm));
        } catch (DataIntegrityViolationException e) {
            throw new ChaveDuplicadaException("Ja existe um pedido deste cliente com esta Idempotency-Key");
        }
    }

    @Override
    public Optional<Pedido> buscarPorId(Long id) {
        return repository.findById(id).map(PedidoRepositoryAdapter::castOrm);
    }

    @Override
    public Optional<Pedido> buscarPorClienteEChave(Long clienteId, String chaveIdempotencia) {
        return repository.findByClienteIdAndChaveIdempotencia(clienteId, chaveIdempotencia)
                .map(PedidoRepositoryAdapter::castOrm);
    }

    @Override
    public Optional<Pedido> buscarPorIdComLock(Long id) {
        return repository.buscarComLock(id).map(PedidoRepositoryAdapter::castOrm);
    }

    @Override
    public List<Pedido> listarTodos() {
        return repository.findAllByOrderByCriadoEmDesc().stream()
                .map(PedidoRepositoryAdapter::castOrm)
                .toList();
    }

    @Override
    public List<Pedido> listarPorStatus(StatusPedido status) {
        return repository.findByStatusOrderByCriadoEmAsc(status).stream()
                .map(PedidoRepositoryAdapter::castOrm)
                .toList();
    }

    @Override
    public List<Pedido> listarPorCliente(Long clienteId) {
        return repository.findByClienteIdOrderByCriadoEmDesc(clienteId).stream()
                .map(PedidoRepositoryAdapter::castOrm)
                .toList();
    }
}
