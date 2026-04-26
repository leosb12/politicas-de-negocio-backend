package com.leo.politicas_de_negocio.instancias.dto;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MisTramiteCardResponse {
    private String id;
    private String codigoTramite;
    private String nombre;
    private EstadoInstancia estadoInstancia;
    private LocalDateTime fechaCreacion;
}
