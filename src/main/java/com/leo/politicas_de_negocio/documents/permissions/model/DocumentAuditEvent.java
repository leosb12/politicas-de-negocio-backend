package com.leo.politicas_de_negocio.documents.permissions.model;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "document_audit_events")
@CompoundIndex(name = "idx_doc_audit_documento_fecha", def = "{'documentoId': 1, 'fechaHora': -1}")
@CompoundIndex(name = "idx_doc_audit_tramite_fecha", def = "{'tramiteId': 1, 'fechaHora': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAuditEvent {

    @Id
    private String id;

    @Indexed(name = "idx_doc_audit_documento")
    private String documentoId;
    private String campoId;
    private String tramiteId;
    private String clienteId;
    private String politicaId;
    private String nodoId;
    private DocumentAuditAction accion;
    private String usuarioId;
    private String usuarioNombre;
    private String rol;
    private String departamentoId;
    private String departamentoNombre;
    private LocalDateTime fechaHora;
    private String ip;
    private String userAgent;
    private String detalle;
    private DocumentAuditResult resultado;
}
