package com.leo.politicas_de_negocio.archivos.storage.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArchivoStoredObject {
    private String nombreGuardado;
    private String rutaOKey;
    private String storageType;
    private String urlAcceso;
    private String bucket;
}
