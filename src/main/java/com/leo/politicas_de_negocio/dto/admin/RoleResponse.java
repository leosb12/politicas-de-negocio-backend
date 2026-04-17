package com.leo.politicas_de_negocio.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoleResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Boolean sistema;
}