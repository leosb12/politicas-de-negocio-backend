package com.leo.politicas_de_negocio.archivos.storage.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArchivoStorageRequest {
    private String nombreGuardado;
    private String contentType;
    private byte[] contenido;
    private String subdirectorio;
}
