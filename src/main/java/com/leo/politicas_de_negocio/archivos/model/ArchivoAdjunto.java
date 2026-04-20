package com.leo.politicas_de_negocio.archivos.model;

import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "archivos_adjuntos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArchivoAdjunto {

    @Id
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
