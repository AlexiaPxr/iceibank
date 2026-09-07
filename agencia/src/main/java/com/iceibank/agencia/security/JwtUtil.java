package com.iceibank.agencia.security;

import com.iceibank.agencia.config.AgenciaProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String segredoConfigurado;

    // 15 minutos por padrao para tokens de usuario (Parte F).
    @Value("${jwt.expiration-ms:900000}")
    private long expiracaoMs;

    // Token de vida bem curta, usado SOMENTE nas chamadas agencia-a-agencia
    // (ex.: creditar-remoto). Ver RESPOSTAS.md, Parte F, para a justificativa
    // de usar um token separado do token de usuario aqui.
    @Value("${jwt.internal-expiration-ms:60000}")
    private long expiracaoInternaMs;

    private final AgenciaProperties agenciaProperties;

    public JwtUtil(AgenciaProperties agenciaProperties) {
        this.agenciaProperties = agenciaProperties;
    }

    private SecretKey chave() {
        byte[] bytes = segredoConfigurado.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret precisa ter pelo menos 32 caracteres (256 bits) para HS256. " +
                            "Configure a variável de ambiente JWT_SECRET."
            );
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String gerarToken(int idConta) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoMs);
        return Jwts.builder()
                .setSubject(String.valueOf(idConta))
                .claim("tipo", "usuario")
                .claim("agenciaEmissora", agenciaProperties.getIdAgencia())
                .setIssuedAt(agora)
                .setExpiration(expiracao)
                .signWith(chave(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String gerarTokenInterno(int idAgenciaOrigem) {
        Date agora = new Date();
        Date expiracao = new Date(agora.getTime() + expiracaoInternaMs);
        return Jwts.builder()
                .setSubject("sistema-interno")
                .claim("tipo", "interno")
                .claim("agenciaEmissora", idAgenciaOrigem)
                .setIssuedAt(agora)
                .setExpiration(expiracao)
                .signWith(chave(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims validarToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(chave())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long getExpiracaoMs() {
        return expiracaoMs;
    }
}
