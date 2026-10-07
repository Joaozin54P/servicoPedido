package com.example.servicopedido.integration.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// resposta do GET /produtos/{id} do servicoCardapio (ignora os campos que nao usamos)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProdutoCardapioResponse(
        Long id,
        String nome,
        BigDecimal preco,
        Boolean disponivel
) {
}
