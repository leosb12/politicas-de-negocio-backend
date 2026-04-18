package com.leo.politicas_de_negocio.departamentos.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DepartamentoResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
    private Long totalUsuarios;
}