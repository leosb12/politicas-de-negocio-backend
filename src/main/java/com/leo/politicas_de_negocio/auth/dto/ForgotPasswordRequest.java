package com.leo.politicas_de_negocio.auth.dto;

import lombok.Data;

@Data
public class ForgotPasswordRequest {
    private String email;
}