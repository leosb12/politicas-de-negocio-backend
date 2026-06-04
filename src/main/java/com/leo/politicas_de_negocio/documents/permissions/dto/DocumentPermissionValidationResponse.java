package com.leo.politicas_de_negocio.documents.permissions.dto;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentPermissionAction;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentPermissionValidationResponse {

    private Boolean permitido;
    private String motivo;
    private String configId;
    private String campoId;
    private DocumentPermissionAction accion;
    private String reglaAplicadaTipo;
    private String reglaAplicadaSujetoId;
    private String reglaAplicadaSujetoNombre;
}
