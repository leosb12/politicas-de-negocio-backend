package com.leo.politicas_de_negocio.politicas.dto;

import lombok.Data;

@Data
public class CreatePoliticaRequest {
    private String nombre;
    private String descripcion;
}
