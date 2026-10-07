package com.example.servicopedido.controller.dto.request;

// {"status": "EM_PREPARO"}  ou  {"status": "PRONTO"}
public record AlterarStatusRequest(
        String status
) {
}
