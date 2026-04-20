package com.leo.politicas_de_negocio.archivos.storage.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArchivoContenido {
    private byte[] contenido;
    private String contentType;
}
