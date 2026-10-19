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
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.ProdutoCardapio;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.event.PedidoStatusAlteradoEvent;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.repository.jpa.PedidoRepositoryWithJpa;

// Teste de verdade: Spring + banco H2 + transacoes reais (igual ao LeilaoServiceTest do projeto antigo).
// Varias threads disparam as mesmas operacoes AO MESMO TEMPO para provar que:
//   - clique duplo / varios cliques em "pagar" criam UM pedido so (restricao unica + Idempotency-Key)
//   - retirar duas vezes, ou cancelar na hora que a cozinha comeca, so deixa uma passar (lock no banco)
// Nao precisa de servicoCardapio nem de RabbitMQ: o cardapio e trocado por uma versao de teste e os eventos so sao contados.
@SpringBootTest(properties = "app.mensageria.habilitada=false")
class PedidoConcorrenciaTest {

    @Autowired
    private CriarPedidoService criar;

    @Autowired
    private CancelarPedidoService cancelar;

    @Autowired
    private AtualizarStatusCozinhaService cozinha;

    @Autowired
    private RetirarPedidoService retirar;

    @Autowired
    private ConsultarPedidoService consultar;

    @Autowired
    private PedidoRepositoryWithJpa pedidosNoBanco;

    @Autowired
    private ContadorDeEventos eventos;

    // ---------------------------------------------------------------- pedido / pagamento em dobro

    @Test
    void cincoCliquesAoMesmoTempoCriamUmSoPedido() throws Exception {
        Long cliente = novoCliente();
        String chave = UUID.randomUUID().toString();
        int cliques = 5;

        List<ResultadoCriacao> resultados = emParalelo(cliques,
                () -> criar.criar(cliente, chave, FormaPagamento.PIX, null, carrinho()));

        long criados = resultados.stream().filter(r -> !r.repetido()).count();
        long idsDiferentes = resultados.stream().map(r -> r.pedido().id()).distinct().count();
        int pedidosNoBancoDoCliente = pedidosNoBanco.findByClienteIdOrderByCriadoEmDesc(cliente).size();

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
        Long cliente = novoCliente();
        String chave = UUID.randomUUID().toString();

        ResultadoCriacao primeiro = criar.criar(cliente, chave, FormaPagamento.CREDITO, null, carrinho());
        ResultadoCriacao segundo = criar.criar(cliente, chave, FormaPagamento.CREDITO, null, carrinho());

        assertTrue(!primeiro.repetido());
        assertTrue(segundo.repetido());
        assertEquals(primeiro.pedido().id(), segundo.pedido().id());
        assertEquals(primeiro.pedido().senhaRetirada(), segundo.pedido().senhaRetirada());
        assertEquals(1, pedidosNoBanco.findByClienteIdOrderByCriadoEmDesc(cliente).size());
        assertEquals(1L, eventos.criadosDo(cliente));
    }

    @Test
    void comprasDiferentesComChavesDiferentesSaoDoisPedidos() {
        Long cliente = novoCliente();

        ResultadoCriacao a = criar.criar(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao b = criar.criar(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null, carrinho());

        assertTrue(!a.repetido() && !b.repetido());
        assertTrue(!a.pedido().id().equals(b.pedido().id()));
        assertEquals(2, pedidosNoBanco.findByClienteIdOrderByCriadoEmDesc(cliente).size());
    }

    @Test
    void mesmaChaveDeClientesDiferentesNaoConflita() {
        String chave = UUID.randomUUID().toString();

        ResultadoCriacao ana = criar.criar(novoCliente(), chave, FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao bia = criar.criar(novoCliente(), chave, FormaPagamento.PIX, null, carrinho());

        assertTrue(!ana.repetido() && !bia.repetido());
        assertTrue(!ana.pedido().id().equals(bia.pedido().id()));
    }

    // ---------------------------------------------------------------- lock nas mudancas de status

    @Test
    void retirarDuasVezesAoMesmoTempoSoUmaPassa() throws Exception {
        Long cliente = novoCliente();
        Pedido pedido = pedidoPronto(cliente);

        List<String> respostas = emParalelo(2, () -> {
            try {
                retirar.retirar(pedido.id());
                return "RETIRADO";
            } catch (RegraDeNegocioException e) {
                return "RECUSADO";
            }
        });

        System.out.println("Retirar 2x ao mesmo tempo -> " + respostas);

        assertEquals(1L, respostas.stream().filter("RETIRADO"::equals).count());
        assertEquals(1L, respostas.stream().filter("RECUSADO"::equals).count());
        assertEquals(StatusPedido.RETIRADO, consultar.buscar(pedido.id(), cliente, false).status());
        assertEquals(1L, eventos.finalizadosDo(pedido.id())); // a venda foi para o relatorio uma vez so
        assertEquals(3L, eventos.statusAlteradosDo(pedido.id())); // EM_PREPARO, PRONTO e RETIRADO (o retirar recusado nao conta)
    }

    @Test
    void cancelarNaHoraQueACozinhaComecaSoUmVence() throws Exception {
        Long cliente = novoCliente();
        Pedido pedido = criar.criar(cliente, UUID.randomUUID().toString(), FormaPagamento.DEBITO, null,
                carrinho()).pedido();

        List<String> respostas = emParaleloVarios(List.of(
                () -> {
                    try {
                        cancelar.cancelar(pedido.id(), cliente, false);
                        return "CANCELOU";
                    } catch (RegraDeNegocioException e) {
                        return "CANCELAMENTO_RECUSADO";
                    }
                },
                () -> {
                    try {
                        cozinha.atualizar(pedido.id(), StatusPedido.EM_PREPARO);
                        return "COZINHA_COMECOU";
                    } catch (RegraDeNegocioException e) {
                        return "COZINHA_RECUSADA";
                    }
                }));

        StatusPedido statusFinal = consultar.buscar(pedido.id(), cliente, false).status();
        System.out.println("Cancelar x cozinha comecar -> " + respostas + " | status final: " + statusFinal);

        // exatamente um dos dois vence; nunca "cancelado e em preparo" ao mesmo tempo
        boolean cancelouVenceu = respostas.contains("CANCELOU") && respostas.contains("COZINHA_RECUSADA");
        boolean cozinhaVenceu = respostas.contains("COZINHA_COMECOU") && respostas.contains("CANCELAMENTO_RECUSADO");
        assertTrue(cancelouVenceu || cozinhaVenceu);
        assertEquals(cancelouVenceu ? StatusPedido.CANCELADO : StatusPedido.EM_PREPARO, statusFinal);
    }

    // ---------------------------------------------------------------- apoio

    private Pedido pedidoPronto(Long cliente) {
        Pedido pedido = criar.criar(cliente, UUID.randomUUID().toString(), FormaPagamento.PIX, null,
                carrinho()).pedido();
        cozinha.atualizar(pedido.id(), StatusPedido.EM_PREPARO);
        cozinha.atualizar(pedido.id(), StatusPedido.PRONTO);
        return pedido;
    }

    private static final AtomicLong PROXIMO_CLIENTE = new AtomicLong(1000);

    private static Long novoCliente() {
        return PROXIMO_CLIENTE.incrementAndGet();
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
        ContadorDeEventos contadorDeEventos() {
            return new ContadorDeEventos();
        }
    }

    // Em vez de mandar para o RabbitMQ, so conta quantos eventos do Spring o dominio soltou.
    // Isso testa o caminho de verdade: dominio -> PedidoEventPublisherSpring -> @EventListener.
    static class ContadorDeEventos {
        private final List<PedidoCriadoEvent> criados = new CopyOnWriteArrayList<>();
        private final List<PedidoCanceladoEvent> cancelados = new CopyOnWriteArrayList<>();
        private final List<PedidoFinalizadoEvent> finalizados = new CopyOnWriteArrayList<>();
        private final List<PedidoStatusAlteradoEvent> statusAlterados = new CopyOnWriteArrayList<>();

        @EventListener
        public void aoCriar(PedidoCriadoEvent evento) {
            criados.add(evento);
        }

        @EventListener
        public void aoCancelar(PedidoCanceladoEvent evento) {
            cancelados.add(evento);
        }

        @EventListener
        public void aoFinalizar(PedidoFinalizadoEvent evento) {
            finalizados.add(evento);
        }

        @EventListener
        public void aoMudarDeStatus(PedidoStatusAlteradoEvent evento) {
            statusAlterados.add(evento);
        }

        long criadosDo(Long clienteId) {
            return criados.stream().filter(e -> e.clienteId().equals(clienteId)).count();
        }

        long finalizadosDo(Long pedidoId) {
            return finalizados.stream().filter(e -> e.pedidoId().equals(pedidoId)).count();
        }

        long statusAlteradosDo(Long pedidoId) {
            return statusAlterados.stream().filter(e -> e.pedidoId().equals(pedidoId)).count();
        }
    }
}
