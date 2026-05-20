package org.example.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import org.example.model.User;
import org.example.util.TokenUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(
            @Value("${quizarena.jwt.secret}") String secret,
            @Value("${quizarena.jwt.expiration-ms}") long expirationMs,
            @Value("${quizarena.jwt.issuer:quizarena}") String issuer
    ) {
        this.key = Keys.hmacShaKeyFor(decodeSecret(secret));
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim("login", user.getLogin())
                .issuedAt(now)
                .expiration(expiresAt)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long extractUserId(String token) {
        Claims claims = parseClaims(token);
        if (claims == null) {
            return null;
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Long extractUserIdFromRequest(HttpServletRequest request) {
        String token = TokenUtil.extractToken(request);
        return token != null ? extractUserId(token) : null;
    }

    public boolean isValid(String token) {
        return parseClaims(token) != null;
    }

    private Claims parseClaims(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            return null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private static byte[] decodeSecret(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            return secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        return secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}
