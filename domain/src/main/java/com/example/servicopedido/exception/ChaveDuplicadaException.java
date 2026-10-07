package com.example.servicopedido.exception;

// o banco recusou porque ja existe um pedido deste cliente com a mesma Idempotency-Key
// (duas requisicoes iguais chegaram ao mesmo tempo e a outra ganhou a corrida)
public class ChaveDuplicadaException extends RuntimeException {
    public ChaveDuplicadaException(String mensagem) {
        super(mensagem);
    }
}
