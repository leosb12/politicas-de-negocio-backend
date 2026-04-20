package com.leo.politicas_de_negocio.archivos.storage;

import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoContenido;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;

public interface ArchivoStorageService {

    ArchivoStoredObject almacenar(ArchivoStorageRequest request);

    ArchivoContenido descargar(String rutaOKey);

    void eliminar(String rutaOKey);

    String construirReferenciaAcceso(String rutaOKey);
}
