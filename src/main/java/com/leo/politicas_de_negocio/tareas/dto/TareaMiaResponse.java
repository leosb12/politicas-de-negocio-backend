package com.leo.politicas_de_negocio.tareas.dto;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class TareaMiaResponse {
    private String id;
    private String nombreActividad;
    private EstadoTarea estadoTarea;
    private String instanciaId;
    private String politicaId;
    private String politicaNombre;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaInicio;
    private String prioridad;
    private String responsableActual;
    private String responsableTipo;
    private String responsableId;
    private String codigoTramite;
    private EstadoInstancia estadoInstancia;
    private Map<String, Object> contextoResumen;
    private String recursoRecomendado;
    private String motivoRecomendacion;
}
