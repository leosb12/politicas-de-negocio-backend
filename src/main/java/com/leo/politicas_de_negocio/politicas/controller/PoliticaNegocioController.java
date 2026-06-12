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
import com.leo.politicas_de_negocio.politicas.service.AuditoriaGeneralPoliticaService;
import com.leo.politicas_de_negocio.politicas.dto.PoliticaAuditoriaGeneralResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.leo.politicas_de_negocio.archivos.storage.ArchivoStorageService;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@RestController
@RequestMapping("/api/politicas")
@RequiredArgsConstructor
public class PoliticaNegocioController {

    private final PoliticaNegocioService service;
    private final AuditoriaDocumentalPoliticaService auditoriaDocumentalService;
    private final AuditoriaGeneralPoliticaService auditoriaGeneralService;
    private final ArchivoStorageService storageService;

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

    @GetMapping("/movil/sincronizar")
    public ResponseEntity<List<PoliticaNegocio>> obtenerPoliticasDisponiblesCompleto(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(service.obtenerPoliticasDisponiblesCompleto(actorUserId));
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

    @GetMapping("/{id}/auditoria/general")
    public ResponseEntity<PoliticaAuditoriaGeneralResponse> obtenerAuditoriaGeneral(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id) {
        return ResponseEntity.ok(auditoriaGeneralService.obtenerAuditoriaGeneral(adminUserId, id));
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

    @PostMapping("/{id}/plantilla")
    public ResponseEntity<Map<String, Object>> subirDocumentoPlantilla(
            @PathVariable("id") String id,
            @RequestParam("campoId") String campoId,
            @RequestParam("archivo") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar un archivo valido");
        }
        String nombreOriginal = file.getOriginalFilename();
        if (nombreOriginal == null) {
            nombreOriginal = "archivo";
        }
        // Validar extension
        String extension = "";
        int lastDot = nombreOriginal.lastIndexOf('.');
        if (lastDot > 0) {
            extension = nombreOriginal.substring(lastDot + 1).toLowerCase();
        }
        if (!List.of("docx", "xlsx", "pptx").contains(extension)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unicamente se permiten archivos .docx, .xlsx y .pptx");
        }

        String mimeType = file.getContentType();
        String nombreGuardado = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        try {
            ArchivoStoredObject stored = storageService.almacenar(
                ArchivoStorageRequest.builder()
                    .nombreGuardado(nombreGuardado)
                    .contentType(mimeType)
                    .contenido(file.getBytes())
                    .subdirectorio("politicas/" + id + "/plantillas/" + campoId)
                    .build()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("nombreOriginal", nombreOriginal);
            response.put("extension", extension);
            response.put("mimeType", mimeType);
            response.put("url", stored.getUrlAcceso());
            response.put("storageKey", stored.getRutaOKey());
            response.put("fechaSubida", java.time.LocalDateTime.now().toString());

            return ResponseEntity.ok(response);
        } catch (java.io.IOException ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al leer el archivo plantilla: " + ex.getMessage());
        }
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
