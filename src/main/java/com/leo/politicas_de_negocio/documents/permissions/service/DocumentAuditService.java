package com.leo.politicas_de_negocio.documents.permissions.service;

import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentAuditEvent;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import com.leo.politicas_de_negocio.documents.permissions.repository.DocumentAuditEventRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentAuditService {

    private static final Logger log = LoggerFactory.getLogger(DocumentAuditService.class);

    private final DocumentAuditEventRepository repository;

    public DocumentAuditEventResponse registrarEventoAuditoria(DocumentAuditEventRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos del evento de auditoria");
        }
        if (request.getAccion() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar la accion de auditoria documental");
        }

        DocumentAuditEvent event = DocumentAuditEvent.builder()
                .documentoId(normalizar(request.getDocumentoId()))
                .campoId(normalizar(request.getCampoId()))
                .tramiteId(normalizar(request.getTramiteId()))
                .clienteId(normalizar(request.getClienteId()))
                .politicaId(normalizar(request.getPoliticaId()))
                .nodoId(normalizar(request.getNodoId()))
                .accion(request.getAccion())
                .usuarioId(normalizar(request.getUsuarioId()))
                .usuarioNombre(normalizar(request.getUsuarioNombre()))
                .rol(normalizar(request.getRol()))
                .departamentoId(normalizar(request.getDepartamentoId()))
                .departamentoNombre(normalizar(request.getDepartamentoNombre()))
                .fechaHora(request.getFechaHora() != null ? request.getFechaHora() : LocalDateTime.now())
                .ip(normalizar(request.getIp()))
                .userAgent(normalizar(request.getUserAgent()))
                .detalle(normalizar(request.getDetalle()))
                .resultado(request.getResultado() != null ? request.getResultado() : DocumentAuditResult.PERMITIDO)
                .build();

        DocumentAuditEvent saved = repository.save(event);
        log.info("Evento de auditoria documental registrado: accion={}, documentoId={}, campoId={}, usuarioId={}, resultado={}",
                saved.getAccion(), saved.getDocumentoId(), saved.getCampoId(), saved.getUsuarioId(), saved.getResultado());
        return toResponse(saved);
    }

    public List<DocumentAuditEventResponse> obtenerEventosPorDocumento(String documentoId) {
        String id = normalizar(documentoId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar documentoId");
        }
        return repository.findByDocumentoIdOrderByFechaHoraDesc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<DocumentAuditEventResponse> obtenerEventosPorTramite(String tramiteId) {
        String id = normalizar(tramiteId);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe indicar tramiteId");
        }
        return repository.findByTramiteIdOrderByFechaHoraDesc(id).stream()
                .map(this::toResponse)
                .toList();
    }

    public DocumentAuditEventResponse toResponse(DocumentAuditEvent event) {
        return DocumentAuditEventResponse.builder()
                .id(event.getId())
                .documentoId(event.getDocumentoId())
                .campoId(event.getCampoId())
                .tramiteId(event.getTramiteId())
                .clienteId(event.getClienteId())
                .politicaId(event.getPoliticaId())
                .nodoId(event.getNodoId())
                .accion(event.getAccion())
                .usuarioId(event.getUsuarioId())
                .usuarioNombre(event.getUsuarioNombre())
                .rol(event.getRol())
                .departamentoId(event.getDepartamentoId())
                .departamentoNombre(event.getDepartamentoNombre())
                .fechaHora(event.getFechaHora())
                .ip(event.getIp())
                .userAgent(event.getUserAgent())
                .detalle(event.getDetalle())
                .resultado(event.getResultado())
                .build();
    }

    private String normalizar(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
