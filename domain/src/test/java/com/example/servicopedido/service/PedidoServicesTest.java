package com.example.servicopedido.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.ProdutoCardapio;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.event.PedidoStatusAlteradoEvent;
import com.example.servicopedido.exception.AcessoNegadoException;
import com.example.servicopedido.exception.ChaveDuplicadaException;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;

// teste do dominio puro: repositorio, cardapio e eventos sao "falsos" (em memoria).
// Cada servico (criar, cancelar, retirar, cozinha, consultar) e testado separado, do jeito que o Spring os monta.
class PedidoServicesTest {

    private static final Long ANA = 1L;
    private static final Long BIA = 2L;
    private static final Long COZINHA = 99L;

    private FakePedidoRepository repository;
    private FakePublisher publisher;

    private CriarPedidoService criar;
    private CancelarPedidoService cancelar;
    private AtualizarStatusCozinhaService cozinha;
    private RetirarPedidoService retirar;
    private ConsultarPedidoService consultar;

    @BeforeEach
    void preparar() {
        repository = new FakePedidoRepository();
        publisher = new FakePublisher();
        CardapioIntegration cardapio = new CardapioIntegration() {
            @Override
            public ProdutoCardapio buscarProduto(Long id) {
                if (id == 1L) return new ProdutoCardapio(1L, "Burger Bacon", new BigDecimal("29.90"), true);
                if (id == 2L) return new ProdutoCardapio(2L, "Batata Media", new BigDecimal("12.90"), true);
                if (id == 3L) return new ProdutoCardapio(3L, "Sundae (acabou)", new BigDecimal("11.90"), false);
                throw new RegraDeNegocioException("Produto " + id + " nao existe no cardapio");
            }

            @Override
            public AdicionalCardapio buscarAdicional(Long id) {
                return new AdicionalCardapio(id, "Bacon extra", new BigDecimal("4.00"), true);
            }
        };

        PedidoRegras regras = new PedidoRegras(repository, publisher);
        criar = new CriarPedidoService(repository, cardapio, new SalvarPedidoService(repository, publisher));
        cancelar = new CancelarPedidoService(regras, publisher);
        cozinha = new AtualizarStatusCozinhaService(regras);
        retirar = new RetirarPedidoService(regras, publisher);
        consultar = new ConsultarPedidoService(repository, regras);
    }

    private static String chave() {
        return UUID.randomUUID().toString();
    }

    private static List<ItemSolicitado> carrinho() {
        return List.of(
                new ItemSolicitado(1L, 2, List.of(10L), "sem cebola"),
                new ItemSolicitado(2L, 1, null, null));
    }

    private Pedido novoPedido() {
        return criar.criar(ANA, chave(), FormaPagamento.PIX, null, carrinho()).pedido();
    }

    // ---------- criar ----------

    @Test
    void calculaTotalComAdicionaisEPublicaPedidoCriado() {
        Pedido pedido = novoPedido();

        // 2 x (29,90 + 4,00) + 1 x 12,90 = 80,70
        assertEquals(0, new BigDecimal("80.70").compareTo(pedido.total()));
        assertEquals(StatusPedido.CRIADO, pedido.status());
        assertEquals("001", pedido.senhaRetirada());
        assertEquals(1, publisher.criados().size());
        assertEquals(ANA, publisher.criados().get(0).clienteId());
        assertEquals(List.of("Bacon extra"), publisher.criados().get(0).itens().get(0).adicionais());
    }

    @Test
    void recusaProdutoIndisponivelEPedidoVazio() {
        assertThrows(RegraDeNegocioException.class, () -> criar.criar(
                ANA, chave(), FormaPagamento.PIX, null, List.of(new ItemSolicitado(3L, 1, null, null))));
        assertThrows(RegraDeNegocioException.class, () -> criar.criar(
                ANA, chave(), FormaPagamento.PIX, null, List.of()));
        assertThrows(RegraDeNegocioException.class, () -> criar.criar(
                ANA, chave(), FormaPagamento.PIX, null, List.of(new ItemSolicitado(1L, 0, null, null))));
        assertTrue(publisher.criados().isEmpty());
    }

    // ---------- cozinha / retirar ----------

    @Test
    void cicloCompletoCozinhaRetiradaEEventoParaRelatorio() {
        Pedido pedido = novoPedido();

        assertEquals(StatusPedido.EM_PREPARO, cozinha.atualizar(pedido.id(), StatusPedido.EM_PREPARO).status());
        assertEquals(StatusPedido.PRONTO, cozinha.atualizar(pedido.id(), StatusPedido.PRONTO).status());
        assertTrue(publisher.finalizados().isEmpty());

        Pedido retirado = retirar.retirar(pedido.id());

        assertEquals(StatusPedido.RETIRADO, retirado.status());
        assertEquals(1, publisher.finalizados().size());
        assertEquals("PIX", publisher.finalizados().get(0).formaPagamento());
        assertEquals(ANA, publisher.finalizados().get(0).clienteId());
        assertEquals(0, new BigDecimal("80.70").compareTo(publisher.finalizados().get(0).total()));
    }

    @Test
    void todaMudancaDeStatusDisparaUmEventoDeStatusAlterado() {
        Pedido pedido = novoPedido();

        cozinha.atualizar(pedido.id(), StatusPedido.EM_PREPARO);
        cozinha.atualizar(pedido.id(), StatusPedido.PRONTO);
        retirar.retirar(pedido.id());

        List<PedidoStatusAlteradoEvent> mudancas = publisher.statusAlterados();
        assertEquals(3, mudancas.size());
        assertEquals(StatusPedido.CRIADO, mudancas.get(0).statusAnterior());
        assertEquals(StatusPedido.EM_PREPARO, mudancas.get(0).novoStatus());
        assertEquals(StatusPedido.PRONTO, mudancas.get(1).novoStatus());
        assertEquals(StatusPedido.RETIRADO, mudancas.get(2).novoStatus());
    }

    @Test
    void naoPulaEtapasNemRetiraPedidoQueNaoEstaPronto() {
        Pedido pedido = novoPedido();

        assertThrows(RegraDeNegocioException.class,
                () -> cozinha.atualizar(pedido.id(), StatusPedido.PRONTO)); // pulou EM_PREPARO
        assertThrows(RegraDeNegocioException.class, () -> retirar.retirar(pedido.id()));
        assertThrows(RegraDeNegocioException.class,
                () -> cozinha.atualizar(pedido.id(), StatusPedido.RETIRADO)); // cozinha nao retira
        assertTrue(publisher.statusAlterados().isEmpty()); // transicao recusada nao dispara evento
    }

    // ---------- cancelar ----------

    @Test
    void cancelaSoEnquantoCriadoENaoAvisaRelatorio() {
        Pedido pedido = novoPedido();

        assertEquals(StatusPedido.CANCELADO, cancelar.cancelar(pedido.id(), ANA, false).status());
        assertEquals(1, publisher.cancelados().size());
        assertTrue(publisher.finalizados().isEmpty());

        Pedido outro = novoPedido();
        cozinha.atualizar(outro.id(), StatusPedido.EM_PREPARO);
        assertThrows(RegraDeNegocioException.class,
                () -> cancelar.cancelar(outro.id(), ANA, false)); // cozinha ja comecou
    }

    @Test
    void clienteNaoVeNemCancelaPedidoDeOutroMasFuncionarioVe() {
        Pedido pedido = novoPedido();

        assertThrows(AcessoNegadoException.class, () -> consultar.buscar(pedido.id(), BIA, false));
        assertThrows(AcessoNegadoException.class, () -> cancelar.cancelar(pedido.id(), BIA, false));
        assertEquals(pedido.id(), consultar.buscar(pedido.id(), COZINHA, true).id());
        assertEquals(1, consultar.listarDoCliente(ANA).size());
        assertTrue(consultar.listarDoCliente(BIA).isEmpty());
    }

    // ---------- pedido duplicado / pagamento em dobro ----------

    @Test
    void mesmoPedidoEnviadoDuasVezesNaoCriaOutroNemAvisaACozinhaDeNovo() {
        String chave = chave();

        ResultadoCriacao primeiro = criar.criar(ANA, chave, FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao segundo = criar.criar(ANA, chave, FormaPagamento.PIX, null, carrinho());

        assertTrue(!primeiro.repetido());
        assertTrue(segundo.repetido());
        assertEquals(primeiro.pedido().id(), segundo.pedido().id());
        assertEquals(primeiro.pedido().senhaRetirada(), segundo.pedido().senhaRetirada());
        assertEquals(1, repository.listarTodos().size());
        assertEquals(1, publisher.criados().size());
    }

    @Test
    void doisCliquesAoMesmoTempoCriamUmSoPedido() throws Exception {
        // as duas requisicoes passam juntas pela checagem "ja existe?" (a barreira garante isso),
        // entao quem segura a segunda e a restricao unica do banco
        repository.barreiraDaChecagem = new CyclicBarrier(2);
        String chave = chave();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Callable<ResultadoCriacao> clique =
                () -> criar.criar(ANA, chave, FormaPagamento.CREDITO, null, carrinho());
        Future<ResultadoCriacao> a = pool.submit(clique);
        Future<ResultadoCriacao> b = pool.submit(clique);
        ResultadoCriacao ra = a.get(10, TimeUnit.SECONDS);
        ResultadoCriacao rb = b.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, repository.listarTodos().size());      // um unico pedido no banco
        assertEquals(ra.pedido().id(), rb.pedido().id());      // os dois cliques recebem o mesmo pedido
        assertTrue(ra.repetido() != rb.repetido());            // um criou, o outro so recebeu
        assertEquals(1, publisher.criados().size());           // a cozinha foi avisada uma vez so
    }

    @Test
    void exigeIdempotencyKey() {
        assertThrows(RegraDeNegocioException.class,
                () -> criar.criar(ANA, null, FormaPagamento.PIX, null, carrinho()));
        assertThrows(RegraDeNegocioException.class,
                () -> criar.criar(ANA, "   ", FormaPagamento.PIX, null, carrinho()));
        assertThrows(RegraDeNegocioException.class,
                () -> criar.criar(ANA, "x".repeat(101), FormaPagamento.PIX, null, carrinho()));
        assertTrue(repository.listarTodos().isEmpty());
    }

    @Test
    void duasComprasDiferentesDoMesmoClienteSaoDuasComprasMesmo() {
        ResultadoCriacao a = criar.criar(ANA, chave(), FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao b = criar.criar(ANA, chave(), FormaPagamento.PIX, null, carrinho());

        assertTrue(!a.repetido() && !b.repetido());
        assertEquals(2, repository.listarTodos().size());
    }

    @Test
    void aChaveEDeCadaClienteEntaoDoisClientesPodemUsarOMesmoCodigo() {
        String chave = chave();

        ResultadoCriacao ana = criar.criar(ANA, chave, FormaPagamento.PIX, null, carrinho());
        ResultadoCriacao bia = criar.criar(BIA, chave, FormaPagamento.PIX, null, carrinho());

        assertTrue(!ana.repetido() && !bia.repetido());
        assertTrue(!ana.pedido().id().equals(bia.pedido().id()));
    }

    @Test
    void chaveNaoFicaPresaSeOPedidoFalhouPoisSoFalhaDeVerdadeNaoGravaNada() {
        String chave = chave();

        // 1a tentativa: produto indisponivel -> erro, nada gravado
        assertThrows(RegraDeNegocioException.class, () -> criar.criar(
                ANA, chave, FormaPagamento.PIX, null, List.of(new ItemSolicitado(3L, 1, null, null))));
        assertTrue(repository.listarTodos().isEmpty());

        // o cliente corrige o carrinho e tenta de novo com a mesma chave: funciona
        ResultadoCriacao retentativa = criar.criar(ANA, chave, FormaPagamento.PIX, null, carrinho());
        assertTrue(!retentativa.repetido());
        assertEquals(1, repository.listarTodos().size());
    }

    // ---------- fakes ----------

    static class FakePedidoRepository implements PedidoRepository {
        private final Map<Long, Pedido> banco = new LinkedHashMap<>();
        private long sequencia = 0;

        // so para o teste da corrida: faz as duas requisicoes passarem juntas pela checagem "ja existe?"
        volatile CyclicBarrier barreiraDaChecagem;

        @Override
        public synchronized Pedido salvar(Pedido p) {
            // restricao unica (cliente + chave), igual a que existe no banco de verdade
            if (p.id() == null && p.chaveIdempotencia() != null && banco.values().stream().anyMatch(o ->
                    o.clienteId().equals(p.clienteId()) && p.chaveIdempotencia().equals(o.chaveIdempotencia()))) {
                throw new ChaveDuplicadaException("chave repetida");
            }
            Long id = p.id() != null ? p.id() : ++sequencia;
            Pedido salvo = new Pedido(id, p.clienteId(), p.status(), p.itens(), p.total(), p.formaPagamento(),
                    p.observacao(), p.senhaRetirada(), p.chaveIdempotencia(), p.criadoEm(), p.atualizadoEm());
            banco.put(id, salvo);
            return salvo;
        }

        @Override
        public Optional<Pedido> buscarPorClienteEChave(Long clienteId, String chave) {
            Optional<Pedido> achado;
            synchronized (this) {
                achado = banco.values().stream()
                        .filter(p -> p.clienteId().equals(clienteId) && chave.equals(p.chaveIdempotencia()))
                        .findFirst();
            }
            CyclicBarrier barreira = barreiraDaChecagem;
            if (barreira != null && achado.isEmpty()) {
                try {
                    barreira.await(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return achado;
        }

        @Override
        public Optional<Pedido> buscarPorId(Long id) {
            return Optional.ofNullable(banco.get(id));
        }

        @Override
        public Optional<Pedido> buscarPorIdComLock(Long id) {
            return buscarPorId(id);
        }

        @Override
        public List<Pedido> listarTodos() {
            return new ArrayList<>(banco.values());
        }

        @Override
        public List<Pedido> listarPorStatus(StatusPedido status) {
            return banco.values().stream().filter(p -> p.status() == status).toList();
        }

        @Override
        public List<Pedido> listarPorCliente(Long clienteId) {
            return banco.values().stream().filter(p -> p.clienteId().equals(clienteId)).toList();
        }
    }

    // guarda todos os eventos que o dominio "soltou", na ordem (a camada spring e quem escuta de verdade)
    static class FakePublisher implements PedidoEventPublisher {
        private final List<PedidoEvent> eventos = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void publicar(PedidoEvent evento) {
            eventos.add(evento);
        }

        List<PedidoCriadoEvent> criados() {
            return do_(PedidoCriadoEvent.class);
        }

        List<PedidoCanceladoEvent> cancelados() {
            return do_(PedidoCanceladoEvent.class);
        }

        List<PedidoFinalizadoEvent> finalizados() {
            return do_(PedidoFinalizadoEvent.class);
        }

        List<PedidoStatusAlteradoEvent> statusAlterados() {
            return do_(PedidoStatusAlteradoEvent.class);
        }

        private <T extends PedidoEvent> List<T> do_(Class<T> tipo) {
            return eventos.stream().filter(tipo::isInstance).map(tipo::cast).toList();
        }
    }
}
