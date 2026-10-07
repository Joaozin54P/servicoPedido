package com.example.servicopedido.repository.orm;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_pedido")
public class ItemPedidoOrm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long produtoId;

    private String nomeProduto;

    private BigDecimal precoUnitario;

    private Integer quantidade;

    // nomes dos adicionais separados por "|" (ex: "Bacon extra|Queijo extra")
    @Column(length = 500)
    private String adicionais;

    private BigDecimal valorAdicionais;

    @Column(length = 500)
    private String observacao;

    private BigDecimal subtotal;

    public ItemPedidoOrm() {
    }

    public ItemPedidoOrm(Long id, Long produtoId, String nomeProduto, BigDecimal precoUnitario, Integer quantidade,
                         String adicionais, BigDecimal valorAdicionais, String observacao, BigDecimal subtotal) {
        this.id = id;
        this.produtoId = produtoId;
        this.nomeProduto = nomeProduto;
        this.precoUnitario = precoUnitario;
        this.quantidade = quantidade;
        this.adicionais = adicionais;
        this.valorAdicionais = valorAdicionais;
        this.observacao = observacao;
        this.subtotal = subtotal;
    }

    public Long getId() {
        return id;
    }

    public Long getProdutoId() {
        return produtoId;
    }

    public String getNomeProduto() {
        return nomeProduto;
    }

    public BigDecimal getPrecoUnitario() {
        return precoUnitario;
    }

    public Integer getQuantidade() {
        return quantidade;
    }

    public String getAdicionais() {
        return adicionais;
    }

    public BigDecimal getValorAdicionais() {
        return valorAdicionais;
    }

    public String getObservacao() {
        return observacao;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}
