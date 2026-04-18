package com.leo.politicas_de_negocio.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String id;
    private String nombre;
    private String correo;
    private String rol;
    private String departamentoId;
}