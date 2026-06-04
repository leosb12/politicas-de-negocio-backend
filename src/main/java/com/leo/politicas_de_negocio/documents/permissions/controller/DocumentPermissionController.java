package com.leo.politicas_de_negocio.documents.permissions.controller;

import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventResponse;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigResponse;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationResponse;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentSubjectOptionResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentAuditService;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentPermissionService;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentPermissionSubjectOptionService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/document-permissions")
@RequiredArgsConstructor
public class DocumentPermissionController {

    private final DocumentPermissionService permissionService;
    private final DocumentAuditService auditService;
    private final DocumentPermissionSubjectOptionService subjectOptionService;

    @PostMapping
    public ResponseEntity<DocumentPermissionConfigResponse> crearConfiguracionPermisos(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @RequestBody DocumentPermissionConfigRequest request
    ) {
        String actor = resolverActorUserId(userId, adminUserId);
        DocumentPermissionConfigResponse response = permissionService.crearConfiguracionPermisos(actor, request);
        registrarCambioPermisos(response, actor, userAgent, servletRequest, "Configuracion documental creada");
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentPermissionConfigResponse> actualizarConfiguracionPermisos(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id,
            @RequestBody DocumentPermissionConfigRequest request
    ) {
        String actor = resolverActorUserId(userId, adminUserId);
        DocumentPermissionConfigResponse response = permissionService.actualizarConfiguracionPermisos(actor, id, request);
        registrarCambioPermisos(response, actor, userAgent, servletRequest, "Configuracion documental actualizada");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-field/{campoId}")
    public ResponseEntity<DocumentPermissionConfigResponse> obtenerConfiguracionPorCampo(
            @PathVariable String campoId
    ) {
        return ResponseEntity.ok(permissionService.obtenerConfiguracionPorCampo(campoId));
    }

    @GetMapping("/by-form/{formularioId}")
    public ResponseEntity<List<DocumentPermissionConfigResponse>> obtenerConfiguracionPorFormulario(
            @PathVariable String formularioId
    ) {
        return ResponseEntity.ok(permissionService.obtenerConfiguracionPorFormulario(formularioId));
    }

    @GetMapping("/subjects/{tipoSujeto}/options")
    public ResponseEntity<List<DocumentSubjectOptionResponse>> listarOpcionesSujeto(
            @PathVariable String tipoSujeto
    ) {
        return ResponseEntity.ok(subjectOptionService.listarOpciones(resolverTipoSujeto(tipoSujeto)));
    }

    @PostMapping("/validate")
    public ResponseEntity<DocumentPermissionValidationResponse> validarPermiso(
            @RequestBody DocumentPermissionValidationRequest request
    ) {
        return ResponseEntity.ok(permissionService.validarPermiso(request));
    }

    @GetMapping("/audit/by-document/{documentoId}")
    public ResponseEntity<List<DocumentAuditEventResponse>> obtenerAuditoriaPorDocumento(
            @PathVariable String documentoId
    ) {
        return ResponseEntity.ok(auditService.obtenerEventosPorDocumento(documentoId));
    }

    @GetMapping("/audit/by-tramite/{tramiteId}")
    public ResponseEntity<List<DocumentAuditEventResponse>> obtenerAuditoriaPorTramite(
            @PathVariable String tramiteId
    ) {
        return ResponseEntity.ok(auditService.obtenerEventosPorTramite(tramiteId));
    }

    private void registrarCambioPermisos(
            DocumentPermissionConfigResponse response,
            String actor,
            String userAgent,
            HttpServletRequest servletRequest,
            String detalle
    ) {
        auditService.registrarEventoAuditoria(auditRequest(response, actor, userAgent, servletRequest, detalle));
    }

    private DocumentAuditEventRequest auditRequest(
            DocumentPermissionConfigResponse response,
            String actor,
            String userAgent,
            HttpServletRequest servletRequest,
            String detalle
    ) {
        DocumentAuditEventRequest request = new DocumentAuditEventRequest();
        request.setCampoId(response.getCampoId());
        request.setTramiteId(response.getAlcance() != null ? response.getAlcance().getTramiteId() : null);
        request.setClienteId(response.getAlcance() != null ? response.getAlcance().getClienteId() : null);
        request.setPoliticaId(response.getPoliticaId());
        request.setNodoId(response.getNodoId());
        request.setAccion(DocumentAuditAction.CAMBIAR_PERMISOS);
        request.setUsuarioId(actor);
        request.setIp(resolverIp(servletRequest));
        request.setUserAgent(userAgent);
        request.setDetalle(detalle);
        request.setResultado(DocumentAuditResult.PERMITIDO);
        return request;
    }

    private String resolverActorUserId(String userId, String adminUserId) {
        String normalizadoUser = normalizar(userId);
        if (normalizadoUser != null) {
            return normalizadoUser;
        }

        String normalizadoAdmin = normalizar(adminUserId);
        if (normalizadoAdmin != null) {
            return normalizadoAdmin;
        }

        throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar X-User-Id o X-Admin-User-Id");
    }

    private String resolverIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = normalizar(request.getHeader("X-Forwarded-For"));
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            return comma >= 0 ? forwarded.substring(0, comma).trim() : forwarded;
        }
        return normalizar(request.getRemoteAddr());
    }

    private DocumentSubjectType resolverTipoSujeto(String value) {
        String normalized = normalizar(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar tipoSujeto");
        }
        try {
            return DocumentSubjectType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "tipoSujeto no soportado: " + normalized);
        }
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
