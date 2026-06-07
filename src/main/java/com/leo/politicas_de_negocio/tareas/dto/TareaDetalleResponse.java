package com.leo.politicas_de_negocio.tareas.dto;

import com.leo.politicas_de_negocio.instancias.dto.HistorialEventoResponse;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class TareaDetalleResponse {
    private String id;
    private String instanciaId;
    private EstadoTarea estadoTarea;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String asignadoA;
    private String asignadoANombre;
    /** IDs de los funcionarios del mismo departamento que también trabajaron esta tarea. */
    private List<String> participantesIds;
    /** Nombres resueltos de los participantes. */
    private List<String> participantesNombres;
    private String observaciones;
    private ActividadTareaResponse actividad;
    private Map<String, Object> formularioRespuesta;
    private InstanciaDetalleResponse instancia;
    private PoliticaResumenResponse politica;
    private List<HistorialEventoResponse> historialRelevante;
    
    private String prioridad;
    private String recursoRecomendado;
    private String recursoRecomendadoNombre;
    private String motivoRecomendacion;

    @Data
    @Builder
    public static class ActividadTareaResponse {
        private String nodoId;
        private String nombreActividad;
        private String responsableTipo;
        private String responsableId;
        private List<CampoFormulario> formularioDefinicion;
    }

    @Data
    @Builder
    public static class PoliticaResumenResponse {
        private String id;
        private String nombre;
        private String descripcion;
        private EstadoPolitica estado;
    }
}
