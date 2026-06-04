package com.leo.politicas_de_negocio.documents.permissions.dto;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentPermissionAction;
import lombok.Data;

@Data
public class DocumentPermissionValidationRequest {

    private String usuarioId;
    private String rol;
    private String departamentoId;
    private String clienteId;
    private String tramiteId;
    private String campoId;
    private DocumentPermissionAction accion;
}
