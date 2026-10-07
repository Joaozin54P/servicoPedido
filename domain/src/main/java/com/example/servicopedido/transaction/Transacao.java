package com.example.servicopedido.transaction;

import java.util.function.Supplier;

// porta: o dominio nao conhece @Transactional, entao pede "execute isso numa transacao"
public interface Transacao {
    <T> T executar(Supplier<T> acao);
}
