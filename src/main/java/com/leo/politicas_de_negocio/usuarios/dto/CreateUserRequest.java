package com.leo.politicas_de_negocio.usuarios.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String nombre;
    private String correo;
    private String password;
    private String rol;
    private String departamentoId;
    private Boolean activo;
}