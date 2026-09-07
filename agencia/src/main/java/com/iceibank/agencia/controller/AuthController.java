package com.iceibank.agencia.controller;

import com.iceibank.agencia.dto.LoginRequest;
import com.iceibank.agencia.dto.TokenResponse;
import com.iceibank.agencia.service.AuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@RequestBody LoginRequest requisicao) {
        return authService.login(requisicao.id(), requisicao.senha());
    }
}
