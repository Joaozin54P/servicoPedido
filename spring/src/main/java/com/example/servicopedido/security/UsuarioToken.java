package com.example.servicopedido.security;

// o que o servico le de dentro do JWT: o ID de quem e o usuario e o perfil dele
public record UsuarioToken(
        Long id,
        String perfil
) {
}
