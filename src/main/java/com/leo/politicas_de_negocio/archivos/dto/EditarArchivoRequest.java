package com.leo.politicas_de_negocio.archivos.dto;

import lombok.Data;

@Data
public class EditarArchivoRequest {
    private String nombreOriginal;
    private String descripcion;
}
