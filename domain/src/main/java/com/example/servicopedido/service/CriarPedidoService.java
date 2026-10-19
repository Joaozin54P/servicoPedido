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
import com.example.servicopedido.exception.ChaveDuplicadaException;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.integration.CardapioIntegration;
import com.example.servicopedido.repository.PedidoRepository;

// So cria pedido: valida, confere o cardapio, calcula o total e garante que o mesmo pedido nao e criado duas vezes.
public class CriarPedidoService {

    private static final int QUANTIDADE_MAXIMA_POR_ITEM = 20;
    private static final int TAMANHO_MAXIMO_CHAVE = 100;

    private final PedidoRepository pedidoRepository;
    private final CardapioIntegration cardapio;
    private final SalvarPedidoService salvarPedido;

    public CriarPedidoService(PedidoRepository pedidoRepository,
                              CardapioIntegration cardapio,
                              SalvarPedidoService salvarPedido) {
        this.pedidoRepository = pedidoRepository;
        this.cardapio = cardapio;
        this.salvarPedido = salvarPedido;
    }

    // chaveIdempotencia: codigo unico POR PEDIDO, gerado pelo front quando o cliente abre o pagamento
    // (ex: um UUID). Se o cliente clicar duas vezes em "pagar", as duas requisicoes levam a MESMA chave:
    // so a primeira cria o pedido, a segunda recebe o pedido que ja existe (repetido = true).
    public ResultadoCriacao criar(Long clienteId,
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
        Optional<Pedido> jaCriado = pedidoRepository.buscarPorClienteEChave(clienteId, chave);
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
        Pedido novo = new Pedido(null, clienteId, StatusPedido.CRIADO, itens, total,
                formaPagamento, observacao, null, chave, agora, agora);

        try {
            return new ResultadoCriacao(salvarPedido.salvarEPublicar(novo), false);

        } catch (ChaveDuplicadaException e) {
            // 2) DOIS cliques ao mesmo tempo: as duas passaram pela checagem acima, mas o banco
            // (restricao unica cliente+chave) deixou so uma gravar. A outra volta aqui e
            // devolve o pedido que ganhou a corrida: nada de pedido (ou cobranca) em dobro.
            Pedido existente = pedidoRepository.buscarPorClienteEChave(clienteId, chave)
                    .orElseThrow(() -> e);
            return new ResultadoCriacao(existente, true);
        }
    }

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
}
