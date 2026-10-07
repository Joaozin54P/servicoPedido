package com.example.servicopedido.transaction;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// implementacao da porta Transacao com a transacao do Spring/JPA
@Component
public class TransacaoSpring implements Transacao {

    private final TransactionTemplate transactionTemplate;

    public TransacaoSpring(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T executar(Supplier<T> acao) {
        return transactionTemplate.execute(status -> acao.get());
    }
}
