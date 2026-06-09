package com.leo.politicas_de_negocio.politicas.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PoliticaAuditoriaGeneralResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private String estado;
    private String creadoPorId;
    private String creadoPorNombre;
    private LocalDateTime fechaCreacion;

    private List<EdicionAuditoriaDto> ediciones;
    private List<IniciadorAuditoriaDto> iniciadores;
    private List<TramiteRealizadoDto> tramitesRealizados;
    private List<ColaboradorAuditoriaDto> colaboradores;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EdicionAuditoriaDto {
        private String id;
        private String tipoAccion; // CREACION, EDICION_METADATOS, EDICION_FLUJO, EDICION_REQUISITOS, CAMBIO_ESTADO, EDICION_CANVAS_COLABORATIVA
        private String usuarioId;
        private String usuarioNombre;
        private LocalDateTime fecha;
        private String detalle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class IniciadorAuditoriaDto {
        private String instanciaId;
        private String codigoTramite;
        private String usuarioId;
        private String usuarioNombre;
        private String usuarioCorreo;
        private LocalDateTime fechaInicio;
        private String estadoInstancia;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TramiteRealizadoDto {
        private String instanciaId;
        private String codigoTramite;
        private String tareaId;
        private String nodoId;
        private String nombreNodo;
        private String funcionarioId;
        private String funcionarioNombre;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFin;
        private String estadoTarea;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColaboradorAuditoriaDto {
        private String usuarioId;
        private String nombre;
        private String correo;
        private String rolEnSistema; // ADMIN, FUNCIONARIO, USUARIO
        private String participacion; // E.g., "Creador", "Editó flujo", "Inició trámite", "Completó tarea"
        private int totalActividades;
    }
}
