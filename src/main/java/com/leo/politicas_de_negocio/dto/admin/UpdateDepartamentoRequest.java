package com.leo.politicas_de_negocio.dto.admin;

import lombok.Data;

@Data
public class UpdateDepartamentoRequest {
    private String nombre;
    private String descripcion;
    private Boolean activo;
}