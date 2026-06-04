package com.leo.politicas_de_negocio.documents.permissions.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAuditConfig {

    private Boolean auditarVisualizacion;
    private Boolean auditarDescarga;
    private Boolean auditarSubida;
    private Boolean auditarEdicion;
    private Boolean auditarEliminacion;
    private Boolean auditarCambioPermisos;
    private Boolean guardarIpDispositivo;
    private Boolean guardarUserAgent;
    private Boolean guardarFechaHora;
    private Boolean guardarUsuarioActor;
}
