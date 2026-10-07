package com.example.servicopedido.integration.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AdicionalCardapioResponse(
        Long id,
        String nome,
        BigDecimal preco,
        Boolean disponivel
) {
}
