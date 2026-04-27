package com.leo.politicas_de_negocio.auth.controller;

import com.leo.politicas_de_negocio.auth.dto.ForgotPasswordRequest;
import com.leo.politicas_de_negocio.auth.dto.ResetPasswordRequest;
import com.leo.politicas_de_negocio.auth.service.PasswordRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {

    private final PasswordRecoveryService passwordRecoveryService;

    public PasswordRecoveryController(PasswordRecoveryService passwordRecoveryService) {
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.OK)
    public void forgotPassword(@RequestBody ForgotPasswordRequest request) {
        passwordRecoveryService.requestPasswordReset(request);
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(request);
    }
}