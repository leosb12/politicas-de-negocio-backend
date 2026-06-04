package com.leo.politicas_de_negocio.documents.permissions.model;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermissionRule {

    private DocumentSubjectType tipoSujeto;
    private String sujetoId;
    private String sujetoNombre;
    private DocumentPermissionSet permisos;
    private LocalDateTime aplicaDesde;
    private LocalDateTime aplicaHasta;
    private Boolean activo;
}
