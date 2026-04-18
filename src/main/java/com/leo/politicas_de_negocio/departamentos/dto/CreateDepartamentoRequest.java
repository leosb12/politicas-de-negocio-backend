package com.leo.politicas_de_negocio.departamentos.dto;

import lombok.Data;

@Data
public class CreateDepartamentoRequest {
    private String nombre;
    private String descripcion;
}