package com.leo.politicas_de_negocio.politicas.dto;

import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class AuditoriaDocumentalPoliticaResponse {
    private String politicaId;
    private Integer totalTareas;
    private Integer totalDocumentos;
    private List<TareaDocumentoResponse> tareas;

    @Data
    @Builder
    public static class TareaDocumentoResponse {
        private String tareaId;
        private String instanciaId;
        private String nodoId;
        private String nombreNodo;
        private EstadoTarea estadoTarea;
        private LocalDateTime fechaCreacion;
        private LocalDateTime fechaInicio;
        private LocalDateTime fechaFin;
        private String asignadoA;
        private String asignadoANombre;
        private Integer totalDocumentos;
        private List<DocumentoAuditoriaResponse> documentos;
    }

    @Data
    @Builder
    public static class DocumentoAuditoriaResponse {
        private String id;
        private String tipoOrigen;
        private String nombre;
        private String campoId;
        private String contentType;
        private String extension;
        private Long tamanoBytes;
        private String estado;
        private String subidoOCreadoPor;
        private String subidoOCreadoPorNombre;
        private LocalDateTime fecha;
    }
}
