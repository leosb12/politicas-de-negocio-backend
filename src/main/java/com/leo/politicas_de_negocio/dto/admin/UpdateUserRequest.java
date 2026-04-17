package com.leo.politicas_de_negocio.dto.admin;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String nombre;
    private String correo;
    private String password;
    private String departamentoId;
    private Boolean activo;
}