package com.leo.politicas_de_negocio.archivos.dto;

import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ArchivoMetadataResponse {
    private String id;
    private String nombreOriginal;
    private String nombreGuardado;
    private String rutaOKey;
    private String storageType;
    private String contentType;
    private String extension;
    private Long tamanoBytes;
    private LocalDateTime fechaSubida;
    private String subidoPor;
    private String instanciaId;
    private String actividadId;
    private String tareaId;
    private String usuarioId;
    private EstadoArchivo estado;
    private String descripcion;
    private String urlAcceso;
    private String bucket;
}
