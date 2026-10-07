package com.example.servicopedido.integration;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.ProdutoCardapio;

// porta de saida para o servicoCardapio (a camada spring faz a chamada HTTP)
public interface CardapioIntegration {
    ProdutoCardapio buscarProduto(Long produtoId);

    AdicionalCardapio buscarAdicional(Long adicionalId);
}
