package com.example.servicopedido.security;

import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// este servico so VALIDA o token (quem emite e o servicoLogin, com o mesmo segredo)
@Component
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    public Optional<UsuarioToken> ler(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new UsuarioToken(claims.getSubject(), claims.get("role", String.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
