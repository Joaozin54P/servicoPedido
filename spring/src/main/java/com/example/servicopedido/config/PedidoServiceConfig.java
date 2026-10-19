package com.example.servicopedido.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;
import com.example.servicopedido.service.AtualizarStatusCozinhaService;
import com.example.servicopedido.service.CancelarPedidoService;
import com.example.servicopedido.service.ConsultarPedidoService;
import com.example.servicopedido.service.CriarPedidoService;
import com.example.servicopedido.service.PedidoRegras;
import com.example.servicopedido.service.RetirarPedidoService;
import com.example.servicopedido.service.SalvarPedidoService;

// As classes de servico sao Java puro (sem @Service). Aqui elas viram beans.
// Os metodos marcados com @Transactional nelas passam a abrir transacao porque o Spring
// cria um "proxy" em volta de cada bean (nao precisa mais de TransactionTemplate nem de Supplier).
@Configuration
public class PedidoServiceConfig {

    @Bean
    public PedidoRegras pedidoRegras(PedidoRepository pedidoRepository, PedidoEventPublisher eventPublisher) {
        return new PedidoRegras(pedidoRepository, eventPublisher);
    }

    @Bean
    public SalvarPedidoService salvarPedidoService(PedidoRepository pedidoRepository,
                                                   PedidoEventPublisher eventPublisher) {
        return new SalvarPedidoService(pedidoRepository, eventPublisher);
    }

    @Bean
    public CriarPedidoService criarPedidoService(PedidoRepository pedidoRepository,
                                                 CardapioIntegration cardapioIntegration,
                                                 SalvarPedidoService salvarPedidoService) {
        return new CriarPedidoService(pedidoRepository, cardapioIntegration, salvarPedidoService);
    }

    @Bean
    public CancelarPedidoService cancelarPedidoService(PedidoRegras regras, PedidoEventPublisher eventPublisher) {
        return new CancelarPedidoService(regras, eventPublisher);
    }

    @Bean
    public AtualizarStatusCozinhaService atualizarStatusCozinhaService(PedidoRegras regras) {
        return new AtualizarStatusCozinhaService(regras);
    }

    @Bean
    public RetirarPedidoService retirarPedidoService(PedidoRegras regras, PedidoEventPublisher eventPublisher) {
        return new RetirarPedidoService(regras, eventPublisher);
    }

    @Bean
    public ConsultarPedidoService consultarPedidoService(PedidoRepository pedidoRepository, PedidoRegras regras) {
        return new ConsultarPedidoService(pedidoRepository, regras);
    }
}
