package com.leo.politicas_de_negocio.tareas.controller;

import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.dto.CompletarTareaRequest;
import com.leo.politicas_de_negocio.tareas.dto.TareaDetalleResponse;
import com.leo.politicas_de_negocio.tareas.dto.TareaMiaResponse;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.service.TareaActividadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tareas")
@RequiredArgsConstructor
public class TareaActividadController {

    private final TareaActividadService tareaService;

    @GetMapping("/mis")
    public ResponseEntity<List<TareaActividad>> listarMisTareas(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.listarMisTareas(actorUserId));
    }

    @GetMapping("/mias")
    public ResponseEntity<List<TareaMiaResponse>> listarMisTareasResumen(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.listarMisTareasResumen(actorUserId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TareaDetalleResponse> obtenerDetalleTarea(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.obtenerDetalleTarea(actorUserId, id));
    }

    @GetMapping("/instancia/{instanciaId}")
    public ResponseEntity<List<TareaMiaResponse>> listarPorInstancia(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String instanciaId
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.listarPorInstancia(actorUserId, instanciaId));
    }

    @PatchMapping("/{id}/tomar")
    public ResponseEntity<TareaActividad> tomarTarea(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.tomarTarea(actorUserId, id));
    }

    @PostMapping("/{id}/tomar")
    public ResponseEntity<TareaActividad> tomarTareaPost(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.tomarTarea(actorUserId, id));
    }

    @PatchMapping("/{id}/completar")
    public ResponseEntity<TareaActividad> completarTarea(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id,
            @RequestBody(required = false) CompletarTareaRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.completarTarea(actorUserId, id, request));
    }

    @PostMapping("/{id}/completar")
    public ResponseEntity<TareaActividad> completarTareaPost(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id,
            @RequestBody(required = false) CompletarTareaRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(tareaService.completarTarea(actorUserId, id, request));
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
}
