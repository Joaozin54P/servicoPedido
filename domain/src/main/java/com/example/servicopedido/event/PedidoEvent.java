package com.example.servicopedido.event;

// marca tudo que "aconteceu" com um pedido. O dominio so diz o que aconteceu;
// quem escuta (RabbitMQ, log...) e decidido na camada spring, com eventos do Spring.
public interface PedidoEvent {
}
