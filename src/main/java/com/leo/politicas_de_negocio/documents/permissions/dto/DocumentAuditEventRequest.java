package com.leo.politicas_de_negocio.documents.permissions.dto;

import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentAuditEventRequest {

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
