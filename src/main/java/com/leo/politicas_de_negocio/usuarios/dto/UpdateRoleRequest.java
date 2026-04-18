package com.leo.politicas_de_negocio.usuarios.dto;

import lombok.Data;

@Data
public class UpdateRoleRequest {
    private String descripcion;
    private Boolean activo;
}