package com.leo.politicas_de_negocio.dto.admin;

import lombok.Data;

@Data
public class CreateDepartamentoRequest {
    private String nombre;
    private String descripcion;
}