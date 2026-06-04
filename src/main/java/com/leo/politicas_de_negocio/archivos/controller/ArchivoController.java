package com.leo.politicas_de_negocio.archivos.controller;

import com.leo.politicas_de_negocio.archivos.dto.ArchivoDescargaResponse;
import com.leo.politicas_de_negocio.archivos.dto.ArchivoMetadataResponse;
import com.leo.politicas_de_negocio.archivos.dto.EditarArchivoRequest;
import com.leo.politicas_de_negocio.archivos.dto.ReemplazarArchivoRequest;
import com.leo.politicas_de_negocio.archivos.dto.SubirArchivoRequest;
import com.leo.politicas_de_negocio.archivos.service.ArchivoService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final ArchivoService archivoService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoMetadataResponse> subirArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @ModelAttribute SubirArchivoRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return new ResponseEntity<>(
                archivoService.subirArchivo(actorUserId, request, resolverIp(servletRequest), userAgent),
                HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArchivoMetadataResponse> obtenerMetadatos(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(archivoService.obtenerMetadatos(actorUserId, id, resolverIp(servletRequest), userAgent));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> descargarArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        ArchivoDescargaResponse archivo = archivoService.descargar(actorUserId, id, resolverIp(servletRequest), userAgent);

        ByteArrayResource resource = new ByteArrayResource(archivo.getContenido());
        String contentDisposition = ContentDisposition.attachment()
                .filename(archivo.getNombreOriginal(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(resolveMediaType(archivo.getContentType()))
                .contentLength(archivo.getContenido().length)
                .body(resource);
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> visualizarArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        ArchivoDescargaResponse archivo = archivoService.visualizar(actorUserId, id, resolverIp(servletRequest), userAgent);

        ByteArrayResource resource = new ByteArrayResource(archivo.getContenido());
        String contentDisposition = ContentDisposition.inline()
                .filename(archivo.getNombreOriginal(), StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(resolveMediaType(archivo.getContentType()))
                .contentLength(archivo.getContenido().length)
                .body(resource);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ArchivoMetadataResponse> editarArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id,
            @RequestBody EditarArchivoRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(archivoService.editar(actorUserId, id, request, resolverIp(servletRequest), userAgent));
    }

    @PutMapping(value = "/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoMetadataResponse> reemplazarArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id,
            @ModelAttribute ReemplazarArchivoRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(archivoService.reemplazar(actorUserId, id, request, resolverIp(servletRequest), userAgent));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarArchivo(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest servletRequest,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        archivoService.eliminar(actorUserId, id, resolverIp(servletRequest), userAgent);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-instancia/{instanciaId}")
    public ResponseEntity<List<ArchivoMetadataResponse>> listarPorInstancia(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String instanciaId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(archivoService.listarPorInstancia(actorUserId, instanciaId));
    }

    @GetMapping("/by-actividad/{actividadId}")
    public ResponseEntity<List<ArchivoMetadataResponse>> listarPorActividad(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String actividadId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(archivoService.listarPorActividad(actorUserId, actividadId));
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
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "Debe enviar X-User-Id o X-Admin-User-Id");
    }

    private String normalizar(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private MediaType resolveMediaType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(value);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
