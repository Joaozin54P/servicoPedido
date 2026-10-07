package com.example.servicopedido.security;

// o que o servico le de dentro do JWT: quem e o usuario e qual o perfil dele
public record UsuarioToken(
        String email,
        String perfil
) {
}
