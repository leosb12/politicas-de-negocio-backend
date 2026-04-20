package com.leo.politicas_de_negocio.politicas.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TramiteDisponibleResponse {
    private String id;
    private String nombre;
    private String descripcion;
}
