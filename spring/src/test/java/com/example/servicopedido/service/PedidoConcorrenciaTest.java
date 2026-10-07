package com.example.servicopedido.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.ProdutoCardapio;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.jpa.PedidoRepositoryWithJpa;

// Teste de verdade: Spring + banco H2 + transacoes reais (igual ao LeilaoServiceTest do projeto antigo).
// Varias threads disparam as mesmas operacoes AO MESMO TEMPO para provar que:
//   - clique duplo / varios cliques em "pagar" criam UM pedido so (restricao unica + Idempotency-Key)
//   - retirar duas vezes, ou cancelar na hora que a cozinha comeca, so deixa uma passar (lock no banco)
// Nao precisa de servicoCardapio nem de RabbitMQ: o cardapio e os eventos sao substituidos por versoes de teste.
@SpringBootTest(properties = "app.mensageria.habilitada=false")
class PedidoConcorrenciaTest {

    @Autowired
    private PedidoService service;

    @Autowired
    private PedidoRepositoryWithJpa pedidosNoBanco;

    @Autowired
    private ContadorDeEventos eventos;

    // ---------------------------------------------------------------- pedido / pagamento em dobro

    @Test
    void cincoCliquesAoMesmoTempoCriamUmSoPedido() throws Exception {
        String cliente = novoCliente();
        String chave = UUID.randomUUID().toString();
        int cliques = 5;

        List<ResultadoCriacao> resultados = emParalelo(cliques,
                () -> service.criarPedido(cliente, chave, FormaPagamento.PIX, null, carrinho()));

        long criados = resultados.stream().filter(r -> !r.repetido()).count();
        long idsDiferentes = resultados.stream().map(r -> r.pedido().id()).distinct().count();
        int pedidosNoBancoDoCliente = pedidosNoBanco.findByClienteEmailOrderByCriadoEmDesc(cliente).size();

        System.out.println("====================================");
        System.out.println("Cliques simultaneos em PAGAR : " + cliques);
        System.out.println("Pedidos criados de verdade   : " + criados);
        System.out.println("Pedidos gravados no banco    : " + pedidosNoBancoDoCliente);
        System.out.println("Cozinha avisada (vezes)      : " + eventos.criadosDo(cliente));
        System.out.println("====================================");

        assertEquals(1L, criados);                    // so um clique "criou"
        assertEquals(1L, idsDiferentes);              // todos receberam o MESMO pedido
        assertEquals(1, pedidosNoBancoDoCliente);     // e so existe um no banco
        assertEquals(1L, eventos.criadosDo(cliente)); // a cozinha foi avisada uma vez
    }

    @Test
    void segundoCliqueDepoisDoPrimeiroDevolveOMesmoPedido() {
        String cliente = novoCliente();
        String chave = UUID.randomUUID().toString();

        ResultadoCriacao primeiro = service.criarPedido(cliente, chave, FormaPagamento.CREDITO, null, carrinho());
        ResultadoCriacao segundo = service.criarPedido(cliente, chave, FormaPagamento.CREDITO, null, carrinho());

        assertTrue(!primeiro.repetido());
        assertTrue(segundo.repetido());
        assertEquals(primeiro.pedido().id(), segundo.pedido().id());
        assertEquals(primeiro.pedido().senhaRetirada(), segundo.pedido().senhaRetirada());
        assertEquals(1, pedidosNoBanco.findByClienteEmailOrderByCriadoEmDesc(cliente).size());
        assertEquals(1L, eventos.criadosDo(cliente));
    }

    @Test
    void comprasDiferentesComChavesDiferentesSaoDoisPedidos() {
        String cliente = novoCliente();

        ResultadoCriacao a = service.criarPedido(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao b = service.criarPedido(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null, carrinho());

        assertTrue(!a.repetido() && !b.repetido());
        assertTrue(!a.pedido().id().equals(b.pedido().id()));
        assertEquals(2, pedidosNoBanco.findByClienteEmailOrderByCriadoEmDesc(cliente).size());
    }

    @Test
    void mesmaChaveDeClientesDiferentesNaoConflita() {
        String chave = UUID.randomUUID().toString();

        ResultadoCriacao ana = service.criarPedido(novoCliente(), chave, FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao bia = service.criarPedido(novoCliente(), chave, FormaPagamento.PIX, null, carrinho());

        assertTrue(!ana.repetido() && !bia.repetido());
        assertTrue(!ana.pedido().id().equals(bia.pedido().id()));
    }

    // ---------------------------------------------------------------- lock nas mudancas de status

    @Test
    void retirarDuasVezesAoMesmoTempoSoUmaPassa() throws Exception {
        String cliente = novoCliente();
        Pedido pedido = pedidoPronto(cliente);

        List<String> respostas = emParalelo(2, () -> {
            try {
                service.retirarPedido(pedido.id());
                return "RETIRADO";
            } catch (RegraDeNegocioException e) {
                return "RECUSADO";
            }
        });

        System.out.println("Retirar 2x ao mesmo tempo -> " + respostas);

        assertEquals(1L, respostas.stream().filter("RETIRADO"::equals).count());
        assertEquals(1L, respostas.stream().filter("RECUSADO"::equals).count());
        assertEquals(StatusPedido.RETIRADO, service.buscarPedido(pedido.id(), cliente, false).status());
        assertEquals(1L, eventos.finalizadosDo(pedido.id())); // a venda foi para o relatorio uma vez so
    }

    @Test
    void cancelarNaHoraQueACozinhaComecaSoUmVence() throws Exception {
        String cliente = novoCliente();
        Pedido pedido = service.criarPedido(cliente, UUID.randomUUID().toString(), FormaPagamento.DEBITO, null,
                carrinho()).pedido();

        List<String> respostas = emParaleloVarios(List.of(
                () -> {
                    try {
                        service.cancelarPedido(pedido.id(), cliente, false);
                        return "CANCELOU";
                    } catch (RegraDeNegocioException e) {
                        return "CANCELAMENTO_RECUSADO";
                    }
                },
                () -> {
                    try {
                        service.atualizarStatusDaCozinha(pedido.id(), StatusPedido.EM_PREPARO);
                        return "COZINHA_COMECOU";
                    } catch (RegraDeNegocioException e) {
                        return "COZINHA_RECUSADA";
                    }
                }));

        StatusPedido statusFinal = service.buscarPedido(pedido.id(), cliente, false).status();
        System.out.println("Cancelar x cozinha comecar -> " + respostas + " | status final: " + statusFinal);

        // exatamente um dos dois vence; nunca "cancelado e em preparo" ao mesmo tempo
        boolean cancelouVenceu = respostas.contains("CANCELOU") && respostas.contains("COZINHA_RECUSADA");
        boolean cozinhaVenceu = respostas.contains("COZINHA_COMECOU") && respostas.contains("CANCELAMENTO_RECUSADO");
        assertTrue(cancelouVenceu || cozinhaVenceu);
        assertEquals(cancelouVenceu ? StatusPedido.CANCELADO : StatusPedido.EM_PREPARO, statusFinal);
    }

    // ---------------------------------------------------------------- apoio

    private Pedido pedidoPronto(String cliente) {
        Pedido pedido = service.criarPedido(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null,
                carrinho()).pedido();
        service.atualizarStatusDaCozinha(pedido.id(), StatusPedido.EM_PREPARO);
        service.atualizarStatusDaCozinha(pedido.id(), StatusPedido.PRONTO);
        return pedido;
    }

    private static String novoCliente() {
        return "cliente-" + UUID.randomUUID() + "@teste.com";
    }

    private static List<ItemSolicitado> carrinho() {
        return List.of(new ItemSolicitado(1L, 2, List.of(10L), "sem cebola"));
    }

    // dispara a MESMA tarefa em varias threads, todas largando juntas
    private static <T> List<T> emParalelo(int threads, Callable<T> tarefa) throws Exception {
        List<Callable<T>> tarefas = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tarefas.add(tarefa);
        }
        return emParaleloVarios(tarefas);
    }

    // dispara tarefas diferentes ao mesmo tempo (todas esperam o "tiro de largada")
    private static <T> List<T> emParaleloVarios(List<Callable<T>> tarefas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tarefas.size());
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (Callable<T> tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();

            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS)); // se alguma thread deu erro, o teste falha aqui
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------------------------------------------------------------- versoes de teste (sem cardapio e sem RabbitMQ)

    @TestConfiguration
    static class ConfigDeTeste {

        @Bean
        @Primary
        CardapioIntegration cardapioDeTeste() {
            return new CardapioIntegration() {
                @Override
                public ProdutoCardapio buscarProduto(Long id) {
                    if (id == 1L) {
                        return new ProdutoCardapio(1L, "Burger Bacon", new BigDecimal("29.90"), true);
                    }
                    if (id == 2L) {
                        return new ProdutoCardapio(2L, "Batata Media", new BigDecimal("12.90"), true);
                    }
                    throw new RegraDeNegocioException("Produto " + id + " nao existe no cardapio");
                }

                @Override
                public AdicionalCardapio buscarAdicional(Long id) {
                    return new AdicionalCardapio(id, "Bacon extra", new BigDecimal("4.00"), true);
                }
            };
        }

        @Bean
        @Primary
        ContadorDeEventos contadorDeEventos() {
            return new ContadorDeEventos();
        }
    }

    // em vez de mandar para o RabbitMQ, so conta quantos avisos saiam
    static class ContadorDeEventos implements PedidoEventPublisher {
        private final List<PedidoCriadoEvent> criados = new CopyOnWriteArrayList<>();
        private final List<PedidoCanceladoEvent> cancelados = new CopyOnWriteArrayList<>();
        private final List<PedidoFinalizadoEvent> finalizados = new CopyOnWriteArrayList<>();

        @Override
        public void publicarPedidoCriado(PedidoCriadoEvent evento) {
            criados.add(evento);
        }

        @Override
        public void publicarPedidoCancelado(PedidoCanceladoEvent evento) {
            cancelados.add(evento);
        }

        @Override
        public void publicarPedidoFinalizado(PedidoFinalizadoEvent evento) {
            finalizados.add(evento);
        }

        long criadosDo(String clienteEmail) {
            return criados.stream().filter(e -> e.clienteEmail().equals(clienteEmail)).count();
        }

        long finalizadosDo(Long pedidoId) {
            return finalizados.stream().filter(e -> e.pedidoId().equals(pedidoId)).count();
        }
    }
}
