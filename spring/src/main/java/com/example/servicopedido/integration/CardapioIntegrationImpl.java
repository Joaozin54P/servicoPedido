package com.example.servicopedido.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.example.servicopedido.entity.AdicionalCardapio;
import com.example.servicopedido.entity.ProdutoCardapio;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.exception.ServicoIndisponivelException;
import com.example.servicopedido.integration.dto.AdicionalCardapioResponse;
import com.example.servicopedido.integration.dto.ProdutoCardapioResponse;

// chamada HTTP (sincrona) para o servicoCardapio. Os GETs do cardapio sao publicos,
// entao nao precisa mandar token.
@Component
public class CardapioIntegrationImpl implements CardapioIntegration {

    private final RestClient restClient;

    public CardapioIntegrationImpl(@Value("${app.cardapio.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public ProdutoCardapio buscarProduto(Long produtoId) {
        ProdutoCardapioResponse resposta = buscar("/produtos/{id}", produtoId, ProdutoCardapioResponse.class,
                "Produto " + produtoId + " nao existe no cardapio");

        return new ProdutoCardapio(resposta.id(), resposta.nome(), resposta.preco(),
                Boolean.TRUE.equals(resposta.disponivel()));
    }

    @Override
    public AdicionalCardapio buscarAdicional(Long adicionalId) {
        AdicionalCardapioResponse resposta = buscar("/adicionais/{id}", adicionalId, AdicionalCardapioResponse.class,
                "Adicional " + adicionalId + " nao existe no cardapio");

        return new AdicionalCardapio(resposta.id(), resposta.nome(), resposta.preco(),
                Boolean.TRUE.equals(resposta.disponivel()));
    }

    private <T> T buscar(String uri, Long id, Class<T> tipo, String mensagemNaoEncontrado) {
        try {
            T resposta = restClient.get().uri(uri, id).retrieve().body(tipo);
            if (resposta == null) {
                throw new RegraDeNegocioException(mensagemNaoEncontrado);
            }
            return resposta;
        } catch (HttpClientErrorException.NotFound e) {
            throw new RegraDeNegocioException(mensagemNaoEncontrado);
        } catch (RestClientException e) {
            throw new ServicoIndisponivelException("O cardapio esta indisponivel no momento, tente novamente");
        }
    }
}
