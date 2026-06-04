package com.leo.politicas_de_negocio.documents.permissions.dto;

import com.leo.politicas_de_negocio.documents.permissions.model.DocumentAuditConfig;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionRule;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionScope;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentCategory;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentConfidentialityLevel;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentFileType;
import lombok.Data;

import java.util.List;

@Data
public class DocumentPermissionConfigRequest {

    private String politicaId;
    private String nodoId;
    private String formularioId;
    private String campoId;
    private String campoNombre;
    private String descripcion;
    private Boolean obligatorio;
    private Boolean permiteMultiplesArchivos;
    private List<DocumentFileType> tiposArchivoPermitidos;
    private Integer tamanoMaximoMb;
    private DocumentCategory categoriaDocumental;
    private DocumentConfidentialityLevel nivelConfidencialidad;
    private DocumentPermissionScope alcance;
    private List<DocumentPermissionRule> reglasPermiso;
    private DocumentAuditConfig auditoria;
    private Boolean activo;
    private String creadoPor;
    private String actualizadoPor;
}
