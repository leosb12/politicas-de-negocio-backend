package com.leo.politicas_de_negocio.instancias.dto;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SeguimientoInstanciaResponse {
    private String instanciaId;
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
    private Long totalTareas;
    private Long tareasAbiertas;
    private Long tareasCompletadas;
    private Long tareasCanceladas;
    private Long tareasRechazadas;
    private Map<String, List<String>> tokensJoin;
    private String laneOrientation;
    private Double laneWidth;
    private Double laneHeight;
    private List<NodoSeguimientoResponse> nodos;
    private List<ConexionSeguimientoResponse> conexiones;
    private List<TareaSeguimientoResponse> tareas;
    private List<DepartamentoActualResponse> departamentosActuales;
    private List<String> nodosActualesIds;

    @Data
    @Builder
    public static class NodoSeguimientoResponse {
        private String id;
        private TipoNodo tipo;
        private String nombre;
        private String departamentoId;
        private String departamentoNombre;
        private String responsableTipo;
        private String responsableId;
        private String responsableNombre;
        private Double posX;
        private Double posY;
        private String estadoSeguimiento;
        private String tareaActualId;
        private EstadoTarea estadoTareaActual;
        private String asignadoA;
        private String asignadoANombre;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFin;
    }

    @Data
    @Builder
    public static class ConexionSeguimientoResponse {
        private String origen;
        private String destino;
        private String puertoOrigen;
        private String puertoDestino;
    }

    @Data
    @Builder
    public static class TareaSeguimientoResponse {
        private String id;
        private String nodoId;
        private String nombreNodo;
        private String responsableTipo;
        private String responsableId;
        private String responsableNombre;
        private EstadoTarea estadoTarea;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFin;
        private String asignadoA;
        private String asignadoANombre;
    }

    @Data
    @Builder
    public static class DepartamentoActualResponse {
        private String departamentoId;
        private String departamentoNombre;
        private String nodoId;
        private String nodoNombre;
        private String tareaId;
        private EstadoTarea estadoTarea;
        private String responsableTipo;
        private String responsableId;
        private String responsableNombre;
        private String asignadoA;
        private String asignadoANombre;
    }
}
