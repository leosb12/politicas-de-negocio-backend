package com.leo.politicas_de_negocio.auth.controller;

import com.leo.politicas_de_negocio.auth.dto.LoginRequest;
import com.leo.politicas_de_negocio.auth.dto.LoginResponse;
import com.leo.politicas_de_negocio.auth.dto.ChangePasswordRequest;
import com.leo.politicas_de_negocio.auth.dto.FuncionarioDepartamentoResponse;
import com.leo.politicas_de_negocio.auth.dto.RegisterMovilRequest;
import com.leo.politicas_de_negocio.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

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

    @PostMapping("/movil/register")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerMovil(@RequestBody RegisterMovilRequest request) {
        return authService.registerMovil(request);
    }

    @PostMapping("/cambiar-contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
    }

    @GetMapping("/funcionario/departamento")
    public FuncionarioDepartamentoResponse getFuncionarioDepartment(
            @RequestHeader("X-User-Id") String funcionarioUserId
    ) {
        return authService.getFuncionarioDepartment(funcionarioUserId);
    }
}
