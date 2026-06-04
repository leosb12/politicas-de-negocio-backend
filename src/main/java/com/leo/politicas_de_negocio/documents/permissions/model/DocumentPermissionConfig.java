package com.leo.politicas_de_negocio.documents.permissions.model;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentCategory;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentConfidentialityLevel;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentFileType;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "document_permission_configs")
@CompoundIndex(name = "idx_doc_perm_form_activo", def = "{'formularioId': 1, 'activo': 1}")
@CompoundIndex(name = "idx_doc_perm_politica_nodo", def = "{'politicaId': 1, 'nodoId': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentPermissionConfig {

    @Id
    private String id;

    private String politicaId;
    private String nodoId;
    private String formularioId;

    @Indexed(name = "idx_doc_perm_campo")
    private String campoId;
    private String campoNombre;
    private TipoCampo tipoCampo;
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
    private LocalDateTime fechaCreacion;
    private String actualizadoPor;
    private LocalDateTime fechaActualizacion;
}
