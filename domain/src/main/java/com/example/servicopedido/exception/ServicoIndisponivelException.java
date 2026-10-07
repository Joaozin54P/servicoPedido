package com.example.servicopedido.exception;

// outro microservico (ex: servicoCardapio) fora do ar
public class ServicoIndisponivelException extends RuntimeException {
    public ServicoIndisponivelException(String mensagem) {
        super(mensagem);
    }
}
