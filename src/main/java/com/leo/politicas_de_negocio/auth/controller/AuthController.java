package com.leo.politicas_de_negocio.auth.controller;

import com.leo.politicas_de_negocio.auth.dto.LoginRequest;
import com.leo.politicas_de_negocio.auth.dto.LoginResponse;
import com.leo.politicas_de_negocio.auth.service.AuthService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/web/login")
    public LoginResponse loginWeb(@RequestBody LoginRequest request) {
        return authService.loginWeb(request);
    }

    @PostMapping("/movil/login")
    public LoginResponse loginMovil(@RequestBody LoginRequest request) {
        return authService.loginMovil(request);
    }
}