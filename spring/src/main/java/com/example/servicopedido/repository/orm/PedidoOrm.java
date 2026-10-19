package com.example.servicopedido.repository.orm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.StatusPedido;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
// restricao unica: o MESMO cliente nao pode ter dois pedidos com a mesma chave de idempotencia.
// E o "lock" da criacao: mesmo com dois cliques simultaneos, o banco so deixa um gravar.
@Table(name = "pedido", uniqueConstraints = @UniqueConstraint(
        name = "uk_pedido_cliente_chave", columnNames = {"cliente_id", "chave_idempotencia"}))
public class PedidoOrm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id")
    private Long clienteId;

    @Enumerated(EnumType.STRING)
    private StatusPedido status;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id")
    private List<ItemPedidoOrm> itens = new ArrayList<>();

    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    private FormaPagamento formaPagamento;

    @Column(length = 500)
    private String observacao;

    private String senhaRetirada;

    @Column(name = "chave_idempotencia", length = 100)
    private String chaveIdempotencia;

    private LocalDateTime criadoEm;

    private LocalDateTime atualizadoEm;

    public PedidoOrm() {
    }

    public PedidoOrm(Long id, Long clienteId, StatusPedido status, List<ItemPedidoOrm> itens, BigDecimal total,
                     FormaPagamento formaPagamento, String observacao, String senhaRetirada,
                     String chaveIdempotencia, LocalDateTime criadoEm, LocalDateTime atualizadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.status = status;
        this.itens = new ArrayList<>(itens);
        this.total = total;
        this.formaPagamento = formaPagamento;
        this.observacao = observacao;
        this.senhaRetirada = senhaRetirada;
        this.chaveIdempotencia = chaveIdempotencia;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
    }

    public Long getId() {
        return id;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public List<ItemPedidoOrm> getItens() {
        return itens;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public FormaPagamento getFormaPagamento() {
        return formaPagamento;
    }

    public String getObservacao() {
        return observacao;
    }

    public String getSenhaRetirada() {
        return senhaRetirada;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
