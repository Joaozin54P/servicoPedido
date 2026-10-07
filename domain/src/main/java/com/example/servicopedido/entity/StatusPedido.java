package com.example.servicopedido.entity;

public enum StatusPedido {
    CRIADO,
    EM_PREPARO,
    PRONTO,
    RETIRADO,
    CANCELADO;

    // regras de quem pode ir para onde:
    // CRIADO -> EM_PREPARO | CANCELADO,  EM_PREPARO -> PRONTO,  PRONTO -> RETIRADO
    public boolean podeIrPara(StatusPedido destino) {
        return switch (this) {
            case CRIADO -> destino == EM_PREPARO || destino == CANCELADO;
            case EM_PREPARO -> destino == PRONTO;
            case PRONTO -> destino == RETIRADO;
            case RETIRADO, CANCELADO -> false;
        };
    }
}
