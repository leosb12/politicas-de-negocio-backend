package com.leo.politicas_de_negocio.documents.permissions.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermissionSet {

    private Boolean leer;
    private Boolean subir;
    private Boolean descargar;
    private Boolean editar;
    private Boolean reemplazar;
    private Boolean eliminar;
    private Boolean administrarPermisos;
    private Boolean colaborar;
}
