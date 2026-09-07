package com.iceibank.agencia.service;

import com.iceibank.agencia.dto.TokenResponse;
import com.iceibank.agencia.exception.CredenciaisInvalidasException;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.security.JwtUtil;
import org.springframework.stereotype.Service;

/**
 * Decisao de design (Parte F, documentada em RESPOSTAS.md): o login e feito
 * por id de conta + senha. A senha e definida na criacao da conta (Parte C)
 * e comparada aqui. Como cada agencia so guarda suas proprias contas, o
 * login so funciona na agencia que e dona daquela conta - o que e coerente
 * com o particionamento do sistema.
 */
@Service
public class AuthService {

    private final ContaService contaService;
    private final JwtUtil jwtUtil;

    public AuthService(ContaService contaService, JwtUtil jwtUtil) {
        this.contaService = contaService;
        this.jwtUtil = jwtUtil;
    }

    public TokenResponse login(int id, String senha) {
        Conta conta = contaService.buscar(id);
        if (conta == null || conta.getSenha() == null || !conta.getSenha().equals(senha)) {
            throw new CredenciaisInvalidasException("Id de conta ou senha inválidos.");
        }
        String token = jwtUtil.gerarToken(id);
        return new TokenResponse(token, jwtUtil.getExpiracaoMs());
    }
}
