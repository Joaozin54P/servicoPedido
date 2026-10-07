package com.example.servicopedido.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;
import com.example.servicopedido.service.PedidoService;
import com.example.servicopedido.transaction.Transacao;

@Configuration
public class PedidoServiceConfig {

    @Bean
    public PedidoService pedidoService(PedidoRepository pedidoRepository,
                                       CardapioIntegration cardapioIntegration,
                                       PedidoEventPublisher eventPublisher,
                                       Transacao transacao) {
        return new PedidoService(pedidoRepository, cardapioIntegration, eventPublisher, transacao);
    }
}
