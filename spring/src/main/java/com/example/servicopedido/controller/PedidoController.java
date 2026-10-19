package com.example.servicopedido.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.servicopedido.controller.adapter.PedidoControllerAdapter;
import com.example.servicopedido.controller.dto.request.AlterarStatusRequest;
import com.example.servicopedido.controller.dto.request.CriarPedidoRequest;
import com.example.servicopedido.controller.dto.response.PedidoResponse;
import com.example.servicopedido.entity.StatusPedido;
import com.example.servicopedido.service.AtualizarStatusCozinhaService;
import com.example.servicopedido.service.CancelarPedidoService;
import com.example.servicopedido.service.ConsultarPedidoService;
import com.example.servicopedido.service.CriarPedidoService;
import com.example.servicopedido.service.ResultadoCriacao;
import com.example.servicopedido.service.RetirarPedidoService;

@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final CriarPedidoService criarPedido;
    private final CancelarPedidoService cancelarPedido;
    private final AtualizarStatusCozinhaService atualizarStatusCozinha;
    private final RetirarPedidoService retirarPedido;
    private final ConsultarPedidoService consultarPedido;

    public PedidoController(CriarPedidoService criarPedido,
                            CancelarPedidoService cancelarPedido,
                            AtualizarStatusCozinhaService atualizarStatusCozinha,
                            RetirarPedidoService retirarPedido,
                            ConsultarPedidoService consultarPedido) {
        this.criarPedido = criarPedido;
        this.cancelarPedido = cancelarPedido;
        this.atualizarStatusCozinha = atualizarStatusCozinha;
        this.retirarPedido = retirarPedido;
        this.consultarPedido = consultarPedido;
    }

    // qualquer usuario logado. Exige o header Idempotency-Key (um UUID novo para cada pedido):
    //   - primeira vez ............ 201 Created + pedido novo
    //   - mesma chave de novo ..... 200 OK + o MESMO pedido (nada e criado nem cobrado em dobro)
    @PostMapping
    public ResponseEntity<PedidoResponse> criar(@RequestBody CriarPedidoRequest request,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String chave,
                                                Authentication authentication) {
        ResultadoCriacao resultado = criarPedido.criar(
                PedidoControllerAdapter.castUsuarioId(authentication),
                chave,
                PedidoControllerAdapter.castFormaPagamento(request.formaPagamento()),
                request.observacao(),
                PedidoControllerAdapter.castItens(request));

        HttpStatus status = resultado.repetido() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(PedidoControllerAdapter.castResponse(resultado.pedido()));
    }

    // historico do proprio cliente
    @GetMapping("/meus")
    public List<PedidoResponse> meus(Authentication authentication) {
        return consultarPedido.listarDoCliente(PedidoControllerAdapter.castUsuarioId(authentication)).stream()
                .map(PedidoControllerAdapter::castResponse)
                .toList();
    }

    // cliente ve so os dele; COZINHA e ADMIN veem qualquer um
    @GetMapping("/{id}")
    public PedidoResponse buscar(@PathVariable Long id, Authentication authentication) {
        return PedidoControllerAdapter.castResponse(
                consultarPedido.buscar(id, PedidoControllerAdapter.castUsuarioId(authentication), ehFuncionario(authentication)));
    }

    // so COZINHA/ADMIN: GET /pedidos  ou  GET /pedidos?status=PRONTO
    @GetMapping
    public List<PedidoResponse> listar(@RequestParam(required = false) StatusPedido status) {
        return consultarPedido.listar(status).stream()
                .map(PedidoControllerAdapter::castResponse)
                .toList();
    }

    // so COZINHA/ADMIN: a cozinha avisa "comecei" (EM_PREPARO) e "ficou pronto" (PRONTO).
    // E o mesmo efeito da mensagem na fila pedido-status-cozinha, mas por REST (serve para testar no Postman).
    @PatchMapping("/{id}/status")
    public PedidoResponse alterarStatus(@PathVariable Long id, @RequestBody AlterarStatusRequest request) {
        return PedidoControllerAdapter.castResponse(atualizarStatusCozinha.atualizar(
                id, PedidoControllerAdapter.castStatus(request.status())));
    }

    // so COZINHA/ADMIN (atendente entrega o pedido PRONTO ao cliente)
    @PostMapping("/{id}/retirar")
    public PedidoResponse retirar(@PathVariable Long id) {
        return PedidoControllerAdapter.castResponse(retirarPedido.retirar(id));
    }

    @PostMapping("/{id}/cancelar")
    public PedidoResponse cancelar(@PathVariable Long id, Authentication authentication) {
        return PedidoControllerAdapter.castResponse(
                cancelarPedido.cancelar(id, PedidoControllerAdapter.castUsuarioId(authentication), ehFuncionario(authentication)));
    }

    private boolean ehFuncionario(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_COZINHA"));
    }
}
