package com.leo.politicas_de_negocio.instancias.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HistorialEventoResponse {
    private String id;
    private String instanciaId;
    private String tareaId;
    private String accion;
    private String usuario;
    private LocalDateTime fecha;
    private String detalle;
}
