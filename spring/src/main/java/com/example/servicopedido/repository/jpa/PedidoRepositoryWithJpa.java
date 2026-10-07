package com.example.servicopedido.repository.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.repository.orm.PedidoOrm;

import jakarta.persistence.LockModeType;

public interface PedidoRepositoryWithJpa extends JpaRepository<PedidoOrm, Long> {

    // trava a linha do pedido (SELECT ... FOR UPDATE) ate o fim da transacao
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PedidoOrm p where p.id = :id")
    Optional<PedidoOrm> buscarComLock(@Param("id") Long id);

    Optional<PedidoOrm> findByClienteEmailAndChaveIdempotencia(String clienteEmail, String chaveIdempotencia);

    List<PedidoOrm> findAllByOrderByCriadoEmDesc();

    // fila da cozinha: mais antigos primeiro
    List<PedidoOrm> findByStatusOrderByCriadoEmAsc(StatusPedido status);

    List<PedidoOrm> findByClienteEmailOrderByCriadoEmDesc(String clienteEmail);
}
