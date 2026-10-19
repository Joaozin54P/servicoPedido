package com.example.servicopedido.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;

// teste simples (sem Spring): confere o texto que vai para o console
class PedidoLogFormatterTest {

    @Test
    void cupomFinalizadoMostraClienteItensETotalEmReais() {
        PedidoFinalizadoEvent evento = new PedidoFinalizadoEvent(1L, 42L, new BigDecimal("1234.50"), "PIX",
                LocalDateTime.of(2026, 10, 3, 11, 49, 57),
                List.of(new PedidoFinalizadoEvent.Item(2L, "Burger Duplo", 1, new BigDecimal("32.90"))));

        String cupom = PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.SO_NO_LOG);

        assertTrue(cupom.contains("PEDIDO FINALIZADO #1"));
        assertTrue(cupom.contains("#42"));
        assertTrue(cupom.contains("03/10/2026 11:49:57"));
        assertTrue(cupom.contains("R$ 32,90"));
        assertTrue(cupom.contains("R$ 1.234,50"));
        assertTrue(cupom.contains("servicoRelatorio"));
    }

    @Test
    void cupomDaCozinhaMostraAdicionaisEObservacaoSemPrecos() {
        PedidoCriadoEvent evento = new PedidoCriadoEvent(7L, "007", 42L, "sem guardanapo",
                LocalDateTime.of(2026, 10, 3, 12, 0, 0),
                List.of(new PedidoCriadoEvent.Item("Burger Bacon", 2, List.of("Bacon extra"), "sem cebola")));

        String cupom = PedidoLogFormatter.criado(evento, PedidoLogFormatter.ENVIADO_RABBITMQ);

        assertTrue(cupom.contains("NOVO PEDIDO #7"));
        assertTrue(cupom.contains("007"));
        assertTrue(cupom.contains(" 2x  Burger Bacon"));
        assertTrue(cupom.contains("+ Bacon extra"));
        assertTrue(cupom.contains("obs: sem cebola"));
        assertTrue(cupom.contains("sem guardanapo"));
        assertTrue(!cupom.contains("R$"));
    }

    @Test
    void nomeMuitoLongoNaoQuebraAColunaDosValores() {
        String nome = "Combo Familia Gigante com Batata Frita Grande e Refrigerante";
        PedidoFinalizadoEvent evento = new PedidoFinalizadoEvent(2L, 42L, new BigDecimal("99.90"), "DINHEIRO",
                LocalDateTime.of(2026, 10, 3, 12, 0, 0),
                List.of(new PedidoFinalizadoEvent.Item(9L, nome, 1, new BigDecimal("99.90"))));

        String cupom = PedidoLogFormatter.finalizado(evento, PedidoLogFormatter.SO_NO_LOG);
        String linhaDoItem = cupom.lines().filter(l -> l.contains("1x")).findFirst().orElseThrow();

        assertTrue(linhaDoItem.contains("..."));
        assertTrue(linhaDoItem.endsWith("R$ 99,90"));
        // todas as linhas de item/total terminam na mesma coluna
        int largura = linhaDoItem.length();
        assertEquals(largura, cupom.lines().filter(l -> l.contains("TOTAL")).findFirst().orElseThrow().length());
    }

    @Test
    void cupomDeCancelamento() {
        String cupom = PedidoLogFormatter.cancelado(new PedidoCanceladoEvent(3L), PedidoLogFormatter.SO_NO_LOG);

        assertTrue(cupom.contains("PEDIDO CANCELADO #3"));
        assertTrue(cupom.contains("servicoCozinha"));
    }
}
