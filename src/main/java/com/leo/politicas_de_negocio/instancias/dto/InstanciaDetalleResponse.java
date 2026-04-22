package com.leo.politicas_de_negocio.instancias.dto;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class InstanciaDetalleResponse {
    private String id;
    private String politicaId;
    private String politicaNombre;
    private String politicaDescripcion;
    private EstadoPolitica politicaEstado;
    private Long politicaVersion;
    private String codigoTramite;
    private EstadoInstancia estadoInstancia;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;
    private LocalDateTime fechaFinalizacion;
    private String creadaPor;
    private String creadaPorNombre;
    private String finalizadaPor;
    private String finalizadaPorNombre;
    private Map<String, Object> datosContexto;
    private Map<String, List<String>> tokensJoin;
    private Long totalTareas;
    private Long tareasAbiertas;
    private Long tareasCompletadas;
    private Long tareasCanceladas;
    private Long tareasRechazadas;
}
