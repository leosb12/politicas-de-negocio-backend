package com.leo.politicas_de_negocio.documents.permissions.dto;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentSubjectOptionResponse {
    private DocumentSubjectType tipoSujeto;
    private String id;
    private String nombre;
    private String detalle;
}
