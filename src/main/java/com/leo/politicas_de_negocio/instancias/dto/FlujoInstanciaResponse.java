package com.leo.politicas_de_negocio.instancias.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class FlujoInstanciaResponse {
    private String instanciaId;
    private String politicaId;
    private String politicaNombre;
    private String codigoTramite;
    private String estadoInstancia;
    private String laneOrientation;
    private Double laneWidth;
    private Double laneHeight;
    private List<NodoFlujoResponse> nodos;
    private List<ConexionFlujoResponse> conexiones;
    private List<TareaFlujoResponse> tareas;
    private List<DepartamentoActualFlujoResponse> departamentosActuales;
    private List<String> nodosActualesIds;

    @Data
    @Builder
    public static class NodoFlujoResponse {
        private String id;
        private String tipo;
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
        private String estadoTareaActual;
        private String asignadoA;
        private String asignadoANombre;
    }

    @Data
    @Builder
    public static class ConexionFlujoResponse {
        private String origen;
        private String destino;
        private String puertoOrigen;
        private String puertoDestino;
    }

    @Data
    @Builder
    public static class TareaFlujoResponse {
        private String id;
        private String nodoId;
        private String nombre;
        private String responsableTipo;
        private String responsableId;
        private String responsableNombre;
        private String estado;
        private String asignadoA;
        private String asignadoANombre;
    }

    @Data
    @Builder
    public static class DepartamentoActualFlujoResponse {
        private String departamentoId;
        private String departamentoNombre;
        private String nodoId;
        private String nodoNombre;
        private String tareaId;
        private String estadoTarea;
        private String responsableTipo;
        private String responsableNombre;
        private String asignadoANombre;
    }
}
