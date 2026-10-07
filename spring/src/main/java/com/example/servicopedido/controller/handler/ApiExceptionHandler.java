package com.example.servicopedido.controller.handler;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.servicopedido.controller.dto.response.ErroResponse;
import com.example.servicopedido.exception.AcessoNegadoException;
import com.example.servicopedido.exception.ChaveDuplicadaException;
import com.example.servicopedido.exception.NaoEncontradoException;
import com.example.servicopedido.exception.RegraDeNegocioException;
import com.example.servicopedido.exception.ServicoIndisponivelException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RegraDeNegocioException.class)
    public ResponseEntity<ErroResponse> regraDeNegocio(RegraDeNegocioException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> jsonInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErroResponse("Corpo da requisicao invalido"));
    }

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(NaoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErroResponse(e.getMessage()));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> acessoNegado(AcessoNegadoException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErroResponse(e.getMessage()));
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    public ResponseEntity<ErroResponse> servicoIndisponivel(ServicoIndisponivelException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErroResponse(e.getMessage()));
    }

    @ExceptionHandler(ChaveDuplicadaException.class)
    public ResponseEntity<ErroResponse> chaveDuplicada(ChaveDuplicadaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErroResponse("Este pedido ja esta sendo processado, tente novamente em instantes"));
    }
}
