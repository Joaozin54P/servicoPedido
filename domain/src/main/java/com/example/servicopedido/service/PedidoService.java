package com.example.servicopedido.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.FormaPagamento;
import com.example.servicopedido.entity.ItemPedido;
import com.example.servicopedido.entity.Pedido;
import com.example.servicopedido.entity.ProdutoCardapio;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.event.PedidoCanceladoEvent;
import com.example.servicopedido.event.PedidoCriadoEvent;
import com.example.servicopedido.event.PedidoFinalizadoEvent;
import com.example.servicopedido.exception.AcessoNegadoException;
import com.example.servicopedido.exception.ChaveDuplicadaException;
import com.example.servicopedido.exception.NaoEncontradoException;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.messaging.PedidoEventPublisher;
import com.example.servicopedido.repository.PedidoRepository;
import com.example.servicopedido.transaction.Transacao;

public class PedidoService {

    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 20;
    private static final int TAMANHO_MAXIMO_CHAVE = 100;

    private final PedidoRepository pedidoRepository;
    private final CardapioIntegration cardapio;
    private final PedidoEventPublisher eventPublisher;
    private final Transacao transacao;

    public PedidoService(PedidoRepository pedidoRepository,
                         CardapioIntegration cardapio,
                         PedidoEventPublisher eventPublisher,
                         Transacao transacao) {
        this.pedidoRepository = pedidoRepository;
        this.cardapio = cardapio;
        this.eventPublisher = eventPublisher;
        this.transacao = transacao;
    }

    // chaveIdempotencia: codigo unico POR PEDIDO, gerado pelo front quando o cliente abre o pagamento
    // (ex: um UUID). Se o cliente clicar duas vezes em "pagar", as duas requisicoes levam a MESMA chave:
    // so a primeira cria o pedido, a segunda recebe o pedido que ja existe (repetido = true).
    public ResultadoCriacao criarPedido(String clienteEmail,
                                        String chaveIdempotencia,
                                        FormaPagamento formaPagamento,
                                        String observacao,
                                        List<ItemSolicitado> solicitados) {

        String chave = chaveIdempotencia == null ? "" : chaveIdempotencia.trim();
        if (chave.isEmpty()) {
            throw new RegraDeNegocioException(
                    "Envie o header Idempotency-Key (um codigo unico por pedido, ex: um UUID)");
        }
        if (chave.length() > TAMANHO_MAXIMO_CHAVE) {
            throw new RegraDeNegocioException(
                    "Idempotency-Key muito grande (maximo " + TAMANHO_MAXIMO_CHAVE + " caracteres)");
        }

        // 1) segundo clique (ou retentativa depois de queda de rede): devolve o pedido que ja existe
        Optional<Pedido> jaCriado = pedidoRepository.buscarPorClienteEChave(clienteEmail, chave);
        if (jaCriado.isPresent()) {
            return new ResultadoCriacao(jaCriado.get(), true);
        }

        if (formaPagamento == null) {
            throw new RegraDeNegocioException("Informe a forma de pagamento");
        }
        if (solicitados == null || solicitados.isEmpty()) {
            throw new RegraDeNegocioException("O pedido precisa ter pelo menos um item");
        }

        // consulta o cardapio ANTES de abrir a transacao (nao segura o banco esperando HTTP)
        List<ItemPedido> itens = new ArrayList<>();
        for (ItemSolicitado solicitado : solicitados) {
            itens.add(montarItem(solicitado));
        }

        BigDecimal total = itens.stream()
                .map(ItemPedido::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDateTime agora = LocalDateTime.now();
        Pedido novo = new Pedido(null, clienteEmail, StatusPedido.CRIADO, itens, total,
                formaPagamento, observacao, null, chave, agora, agora);

        try {
            // salva e avisa a cozinha na mesma transacao: se o RabbitMQ estiver fora do ar,
            // o pedido nao fica "perdido" no banco sem a cozinha saber
            Pedido criado = transacao.executar(() -> {
                Pedido salvo = pedidoRepository.salvar(novo);
                Pedido comSenha = pedidoRepository.salvar(
                        salvo.comSenha(String.format("%03d", salvo.id() % 1000)));

                eventPublisher.publicarPedidoCriado(montarEventoCriado(comSenha));
                return comSenha;
            });
            return new ResultadoCriacao(criado, false);

        } catch (ChaveDuplicadaException e) {
            // 2) DOIS cliques ao mesmo tempo: as duas passaram pela checagem acima, mas o banco
            // (restricao unica cliente+chave) deixou so uma gravar. A outra volta aqui e
            // devolve o pedido que ganhou a corrida: nada de pedido (ou cobranca) em dobro.
            Pedido existente = pedidoRepository.buscarPorClienteEChave(clienteEmail, chave)
                    .orElseThrow(() -> e);
            return new ResultadoCriacao(existente, true);
        }
    }

    public Pedido buscarPedido(Long id, String usuarioEmail, boolean funcionario) {
        Pedido pedido = pedidoRepository.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("Pedido nao encontrado"));
        garantirAcesso(pedido, usuarioEmail, funcionario);
        return pedido;
    }

    public List<Pedido> listarMeusPedidos(String clienteEmail) {
        return pedidoRepository.listarPorCliente(clienteEmail);
    }

    // painel da cozinha / admin. status == null lista todos
    public List<Pedido> listarPedidos(StatusPedido status) {
        return status == null
                ? pedidoRepository.listarTodos()
                : pedidoRepository.listarPorStatus(status);
    }

    // chamado quando chega a mensagem da cozinha (EM_PREPARO ou PRONTO)
    public Pedido atualizarStatusDaCozinha(Long pedidoId, StatusPedido novoStatus) {
        if (novoStatus != StatusPedido.EM_PREPARO && novoStatus != StatusPedido.PRONTO) {
            throw new RegraDeNegocioException("A cozinha so pode informar EM_PREPARO ou PRONTO");
        }

        return transacao.executar(() -> {
            Pedido pedido = buscarComLock(pedidoId);
            garantirTransicao(pedido, novoStatus);
            return pedidoRepository.salvar(pedido.comStatus(novoStatus));
        });
    }

    // cliente pegou o pedido no balcao: a venda esta concluida
    public Pedido retirarPedido(Long pedidoId) {
        return transacao.executar(() -> {
            Pedido pedido = buscarComLock(pedidoId);
            garantirTransicao(pedido, StatusPedido.RETIRADO);

            Pedido retirado = pedidoRepository.salvar(pedido.comStatus(StatusPedido.RETIRADO));
            eventPublisher.publicarPedidoFinalizado(montarEventoFinalizado(retirado));
            return retirado;
        });
    }

    // so da para cancelar enquanto a cozinha ainda nao comecou (status CRIADO)
    public Pedido cancelarPedido(Long pedidoId, String usuarioEmail, boolean funcionario) {
        return transacao.executar(() -> {
            Pedido pedido = buscarComLock(pedidoId);
            garantirAcesso(pedido, usuarioEmail, funcionario);
            garantirTransicao(pedido, StatusPedido.CANCELADO);

            Pedido cancelado = pedidoRepository.salvar(pedido.comStatus(StatusPedido.CANCELADO));
            eventPublisher.publicarPedidoCancelado(new PedidoCanceladoEvent(cancelado.id()));
            return cancelado;
        });
    }

    // ---------- internos ----------

    private ItemPedido montarItem(ItemSolicitado solicitado) {
        if (solicitado.produtoId() == null) {
            throw new RegraDeNegocioException("Informe o produto de cada item");
        }

        int quantidade = solicitado.quantidade() == null ? 0 : solicitado.quantidade();
        if (quantidade <= 0 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
            throw new RegraDeNegocioException(
                    "A quantidade de cada item deve ser de 1 a " + QUANTIDADE_MAXIMA_POR_ITEM);
        }

        ProdutoCardapio produto = cardapio.buscarProduto(solicitado.produtoId());
        if (!produto.disponivel()) {
            throw new RegraDeNegocioException("Produto indisponivel no momento: " + produto.nome());
        }

        List<String> nomesAdicionais = new ArrayList<>();
        BigDecimal valorAdicionais = BigDecimal.ZERO;

        if (solicitado.adicionaisIds() != null) {
            for (Long adicionalId : solicitado.adicionaisIds()) {
                AdicionalCardapio adicional = cardapio.buscarAdicional(adicionalId);
                if (!adicional.disponivel()) {
                    throw new RegraDeNegocioException("Adicional indisponivel no momento: " + adicional.nome());
                }
                nomesAdicionais.add(adicional.nome());
                valorAdicionais = valorAdicionais.add(adicional.preco());
            }
        }

        BigDecimal subtotal = produto.preco()
                .add(valorAdicionais)
                .multiply(BigDecimal.valueOf(quantidade));

        return new ItemPedido(null, produto.id(), produto.nome(), produto.preco(), quantidade,
                nomesAdicionais, valorAdicionais, solicitado.observacao(), subtotal);
    }

    private Pedido buscarComLock(Long pedidoId) {
        return pedidoRepository.buscarPorIdComLock(pedidoId)
                .orElseThrow(() -> new NaoEncontradoException("Pedido nao encontrado"));
    }

    private void garantirAcesso(Pedido pedido, String usuarioEmail, boolean funcionario) {
        if (!funcionario && !pedido.clienteEmail().equals(usuarioEmail)) {
            throw new AcessoNegadoException("Este pedido pertence a outro cliente");
        }
    }

    private void garantirTransicao(Pedido pedido, StatusPedido destino) {
        if (!pedido.status().podeIrPara(destino)) {
            throw new RegraDeNegocioException(
                    "O pedido " + pedido.id() + " esta " + pedido.status() + " e nao pode ir para " + destino);
        }
    }

    private PedidoCriadoEvent montarEventoCriado(Pedido pedido) {
        List<PedidoCriadoEvent.Item> itens = pedido.itens().stream()
                .map(i -> new PedidoCriadoEvent.Item(i.nomeProduto(), i.quantidade(), i.adicionais(), i.observacao()))
                .toList();

        return new PedidoCriadoEvent(pedido.id(), pedido.senhaRetirada(), pedido.clienteEmail(),
                pedido.observacao(), pedido.criadoEm(), itens);
    }

    private PedidoFinalizadoEvent montarEventoFinalizado(Pedido pedido) {
        List<PedidoFinalizadoEvent.Item> itens = pedido.itens().stream()
                .map(i -> new PedidoFinalizadoEvent.Item(i.produtoId(), i.nomeProduto(), i.quantidade(), i.subtotal()))
                .toList();

        return new PedidoFinalizadoEvent(pedido.id(), pedido.clienteEmail(), pedido.total(),
                pedido.formaPagamento().name(), LocalDateTime.now(), itens);
    }
}
