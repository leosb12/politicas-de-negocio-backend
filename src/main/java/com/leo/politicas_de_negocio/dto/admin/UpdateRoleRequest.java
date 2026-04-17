package com.leo.politicas_de_negocio.dto.admin;

import lombok.Data;

@Data
public class UpdateRoleRequest {
    private String descripcion;
    private Boolean activo;
}