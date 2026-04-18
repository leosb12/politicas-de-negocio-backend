package com.leo.politicas_de_negocio.usuarios.dto;

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