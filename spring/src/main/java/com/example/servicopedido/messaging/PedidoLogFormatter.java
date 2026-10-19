package com.example.servicopedido.messaging;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;

// Monta o "cupom" que aparece no console quando um pedido e criado, cancelado ou finalizado.
// So caracteres simples (ASCII) nas bordas, para nao embaralhar no console do Windows.
public final class PedidoLogFormatter {

    public static final String SO_NO_LOG = "mensageria desligada: so no log";
    public static final String ENVIADO_RABBITMQ = "enviado ao RabbitMQ";

    private static final int LARGURA = 60;
    private static final int LARGURA_NOME = 34;
    private static final String LINHA_DUPLA = "=".repeat(LARGURA);
    private static final String LINHA_SIMPLES = "-".repeat(LARGURA);
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Locale PT_BR = Locale.of("pt", "BR");

    private PedidoLogFormatter() {
    }

    // o que a COZINHA precisa saber: o que preparar (sem precos)
    public static String criado(PedidoCriadoEvent evento, String situacao) {
        StringBuilder sb = new StringBuilder();
        cabecalho(sb, "NOVO PEDIDO #" + evento.pedidoId(), "pedido-criado -> servicoCozinha", situacao);
        campo(sb, "Senha de retirada", evento.senhaRetirada());
        campo(sb, "Cliente", "#" + evento.clienteId());
        campo(sb, "Criado em", data(evento.criadoEm()));
        if (preenchido(evento.observacao())) {
            campo(sb, "Observacao", evento.observacao());
        }
        sb.append(LINHA_SIMPLES).append('\n');
        sb.append(String.format("  %-5s%s%n", "QTD", "ITEM"));
        sb.append(LINHA_SIMPLES).append('\n');

        for (PedidoCriadoEvent.Item item : evento.itens()) {
            sb.append(String.format("  %2dx  %s%n", item.quantidade(), item.nomeProduto()));
            detalhes(sb, item.adicionais(), item.observacao());
        }

        sb.append(LINHA_DUPLA);
        return sb.toString();
    }

    public static String cancelado(PedidoCanceladoEvent evento, String situacao) {
        StringBuilder sb = new StringBuilder();
        cabecalho(sb, "PEDIDO CANCELADO #" + evento.pedidoId(), "pedido-cancelado -> servicoCozinha", situacao);
        sb.append("  Cancelado antes de a cozinha comecar o preparo.\n");
        sb.append("  A cozinha deve tirar este pedido da fila.\n");
        sb.append(LINHA_DUPLA);
        return sb.toString();
    }

    // o que o RELATORIO precisa saber: quem comprou, o que e quanto pagou
    public static String finalizado(PedidoFinalizadoEvent evento, String situacao) {
        StringBuilder sb = new StringBuilder();
        cabecalho(sb, "PEDIDO FINALIZADO #" + evento.pedidoId(), "pedido-finalizado -> servicoRelatorio", situacao);
        campo(sb, "Cliente", "#" + evento.clienteId());
        campo(sb, "Pagamento", evento.formaPagamento());
        campo(sb, "Finalizado em", data(evento.finalizadoEm()));
        sb.append(LINHA_SIMPLES).append('\n');
        sb.append(String.format("  %-5s%-" + LARGURA_NOME + "s %12s%n", "QTD", "ITEM", "SUBTOTAL"));
        sb.append(LINHA_SIMPLES).append('\n');

        for (PedidoFinalizadoEvent.Item item : evento.itens()) {
            sb.append(String.format("  %2dx  %-" + LARGURA_NOME + "s %12s%n",
                    item.quantidade(), truncar(item.nomeProduto(), LARGURA_NOME), dinheiro(item.subtotal())));
        }

        sb.append(LINHA_SIMPLES).append('\n');
        sb.append(String.format("  %-" + (LARGURA_NOME + 5) + "s %12s%n", "TOTAL", dinheiro(evento.total())));
        sb.append(LINHA_DUPLA);
        return sb.toString();
    }

    // ---------- pedacos ----------

    private static void cabecalho(StringBuilder sb, String titulo, String fila, String situacao) {
        sb.append(LINHA_DUPLA).append('\n');
        sb.append("  ").append(titulo).append('\n');
        sb.append("  ").append(fila).append("  (").append(situacao).append(")\n");
        sb.append(LINHA_DUPLA).append('\n');
    }

    private static void campo(StringBuilder sb, String rotulo, String valor) {
        sb.append(String.format("  %-18s: %s%n", rotulo, valor));
    }

    private static void detalhes(StringBuilder sb, List<String> adicionais, String observacao) {
        if (adicionais != null) {
            for (String adicional : adicionais) {
                sb.append("        + ").append(adicional).append('\n');
            }
        }
        if (preenchido(observacao)) {
            sb.append("        obs: ").append(observacao).append('\n');
        }
    }

    // 1234.5 -> "R$ 1.234,50"
    static String dinheiro(BigDecimal valor) {
        return valor == null ? "-" : "R$ " + String.format(PT_BR, "%,.2f", valor);
    }

    private static String data(LocalDateTime data) {
        return data == null ? "-" : data.format(DATA_HORA);
    }

    private static String truncar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 3) + "...";
    }

    private static boolean preenchido(String texto) {
        return texto != null && !texto.isBlank();
    }
}
