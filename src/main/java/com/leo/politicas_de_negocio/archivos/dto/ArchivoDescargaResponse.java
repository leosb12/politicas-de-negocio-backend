package com.leo.politicas_de_negocio.archivos.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArchivoDescargaResponse {
    private byte[] contenido;
    private String nombreOriginal;
    private String contentType;
}
