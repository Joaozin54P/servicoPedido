package com.example.servicopedido.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.example.servicopedido.config.RabbitMQConfig;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.messaging.dto.StatusCozinhaMessage;
import com.example.servicopedido.service.AtualizarStatusCozinhaService;

// a cozinha avisa "comecei a preparar" / "ficou pronto" e o status do pedido muda aqui
@ConditionalOnProperty(prefix = "app.mensageria", name = "habilitada", havingValue = "true")
@Component
public class StatusCozinhaConsumer {

    private static final Logger log = LoggerFactory.getLogger(StatusCozinhaConsumer.class);

    private final AtualizarStatusCozinhaService atualizarStatusCozinha;

    public StatusCozinhaConsumer(AtualizarStatusCozinhaService atualizarStatusCozinha) {
        this.atualizarStatusCozinha = atualizarStatusCozinha;
    }

    @RabbitListener(queues = RabbitMQConfig.FILA_STATUS_COZINHA)
    public void receber(StatusCozinhaMessage mensagem) {
        try {
            StatusPedido status = StatusPedido.valueOf(mensagem.status().trim().toUpperCase());
            atualizarStatusCozinha.atualizar(mensagem.pedidoId(), status);
            log.info("COZINHA -> pedido #{} agora esta {}", mensagem.pedidoId(), status);
        } catch (RuntimeException e) {
            // mensagem invalida ou transicao nao permitida: registra e descarta
            // (reenviar a mesma mensagem nao resolveria)
            log.warn("Mensagem da cozinha ignorada ({}): {}", mensagem, e.getMessage());
        }
    }
}
