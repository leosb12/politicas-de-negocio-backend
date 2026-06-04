package com.leo.politicas_de_negocio.archivos.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class SubirArchivoRequest {
    private MultipartFile archivo;
    private String instanciaId;
    private String actividadId;
    private String tareaId;
    private String usuarioId;
    private String campoId;
    private String tramiteId;
    private String clienteId;
    private String politicaId;
    private String nodoId;
    private String descripcion;
}
