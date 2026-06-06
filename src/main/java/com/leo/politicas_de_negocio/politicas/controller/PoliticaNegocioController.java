package com.leo.politicas_de_negocio.politicas.controller;

import com.leo.politicas_de_negocio.documents.model.DocumentoVersion;
import com.leo.politicas_de_negocio.politicas.dto.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.AuditoriaDocumentalPoliticaResponse;
import com.leo.politicas_de_negocio.politicas.dto.TramiteDisponibleResponse;
import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdateRequisitosInicialesRequest;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.service.AuditoriaDocumentalPoliticaService;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/politicas")
@RequiredArgsConstructor
public class PoliticaNegocioController {

    private final PoliticaNegocioService service;
    private final AuditoriaDocumentalPoliticaService auditoriaDocumentalService;

    @PostMapping
    public ResponseEntity<PoliticaNegocio> crearPolitica(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @RequestBody CreatePoliticaRequest request) {
        return new ResponseEntity<>(service.crearPolitica(adminUserId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<PoliticaNegocio>> obtenerTodas(
            @RequestHeader("X-Admin-User-Id") String adminUserId) {
        return ResponseEntity.ok(service.obtenerTodas(adminUserId));
    }

    @GetMapping("/movil/disponibles")
    public ResponseEntity<List<TramiteDisponibleResponse>> obtenerTramitesDisponibles(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(service.obtenerTramitesDisponibles(actorUserId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PoliticaNegocio> obtenerPorId(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.obtenerPorId(adminUserId, id));
    }

    @GetMapping("/{id}/auditoria/documental")
    public ResponseEntity<AuditoriaDocumentalPoliticaResponse> obtenerAuditoriaDocumental(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id) {
        return ResponseEntity.ok(auditoriaDocumentalService.obtenerAuditoriaDocumental(adminUserId, id));
    }

    @GetMapping("/{id}/requisitos-iniciales")
    public ResponseEntity<List<CampoFormulario>> obtenerRequisitosIniciales(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(service.obtenerRequisitosIniciales(actorUserId, id));
    }

    @PutMapping("/{id}/requisitos-iniciales")
    public ResponseEntity<PoliticaNegocio> guardarRequisitosIniciales(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id,
            @RequestBody UpdateRequisitosInicialesRequest request
    ) {
        return ResponseEntity.ok(service.guardarRequisitosIniciales(
                adminUserId,
                id,
                request != null ? request.getRequisitosIniciales() : null
        ));
    }

    @GetMapping("/{id}/auditoria/documental/documentos/{documentoId}/versiones")
    public ResponseEntity<List<DocumentoVersion>> obtenerVersionesDocumentoAuditoria(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id,
            @PathVariable String documentoId) {
        return ResponseEntity.ok(auditoriaDocumentalService.listarVersionesDocumento(adminUserId, id, documentoId));
    }

    @PutMapping("/{id}/flujo")
    public ResponseEntity<PoliticaNegocio> guardarFlujo(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id, 
            @RequestBody UpdateFlujoRequest request) {
        return ResponseEntity.ok(service.guardarFlujo(adminUserId, id, request));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PoliticaNegocio> cambiarEstado(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id, 
            @RequestBody Map<String, String> body) {
        String estadoStr = body.get("estado");
        if (estadoStr == null) {
            return ResponseEntity.badRequest().build();
        }
        EstadoPolitica nuevoEstado = EstadoPolitica.valueOf(estadoStr.toUpperCase());
        return ResponseEntity.ok(service.cambiarEstado(adminUserId, id, nuevoEstado));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PoliticaNegocio> actualizarNombreDescripcion(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id,
            @RequestBody UpdatePoliticaRequest request) {
        return ResponseEntity.ok(
            service.actualizarMetadatos(
                adminUserId,
                id,
                request.getNombre(),
                request.getDescripcion(),
                request.getTipoPolitica(),
                request.getDepartamentoInicioId(),
                request.getRequierePago(),
                request.getMontoPago(),
                request.getMonedaPago(),
                request.getDescripcionPago()
            )
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPolitica(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id
    ) {
        service.eliminarPolitica(adminUserId, id);
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

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
