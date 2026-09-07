package com.iceibank.agencia.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepta toda requisicao (exceto /auth/login) e exige um JWT valido no
 * cabecalho Authorization: Bearer <token>. Sem token, com token invalido ou
 * expirado, responde 401 - exatamente os tres cenarios pedidos na Parte F.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String caminho = request.getRequestURI();
        String metodo = request.getMethod();

        boolean ehLogin = "/auth/login".equals(caminho);
        // Criar conta e uma excecao proposital: e o "abrir conta no banco",
        // que acontece ANTES de existir qualquer credencial para autenticar.
        // Sem essa excecao, ninguem conseguiria criar a primeira conta.
        boolean ehCriarConta = "/contas".equals(caminho) && "POST".equalsIgnoreCase(metodo);

        if (ehLogin || ehCriarConta || "OPTIONS".equalsIgnoreCase(metodo)) {
            filterChain.doFilter(request, response);
            return;
        }

        String cabecalho = request.getHeader("Authorization");
        if (cabecalho == null || !cabecalho.startsWith("Bearer ")) {
            responderNaoAutorizado(response, "Token ausente. Envie 'Authorization: Bearer <token>'.");
            return;
        }

        String token = cabecalho.substring("Bearer ".length());
        try {
            var claims = jwtUtil.validarToken(token);
            var autenticacao = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(autenticacao);
        } catch (JwtException | IllegalArgumentException e) {
            responderNaoAutorizado(response, "Token inválido ou expirado.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void responderNaoAutorizado(HttpServletResponse response, String mensagem) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\": \"" + mensagem + "\"}");
    }
}
