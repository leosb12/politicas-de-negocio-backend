package com.leo.politicas_de_negocio.instancias.controller;

import com.leo.politicas_de_negocio.instancias.dto.CrearInstanciaRequest;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/instancias")
@RequiredArgsConstructor
public class InstanciaPoliticaController {

    private final InstanciaPoliticaService instanciaService;

    @PostMapping
    public ResponseEntity<InstanciaPolitica> crearInstancia(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestBody CrearInstanciaRequest request
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return new ResponseEntity<>(instanciaService.crearInstancia(actorUserId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<InstanciaPolitica>> listarInstancias(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @RequestParam(value = "estado", required = false) String estado
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        EstadoInstancia estadoInstancia = parseEstado(estado);
        return ResponseEntity.ok(instanciaService.listar(actorUserId, estadoInstancia));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstanciaDetalleResponse> obtenerInstancia(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(instanciaService.obtenerDetallePorId(actorUserId, id));
    }

    @GetMapping("/{id}/historial")
    public ResponseEntity<List<HistorialInstancia>> obtenerHistorial(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-Admin-User-Id", required = false) String adminUserId,
            @PathVariable String id
    ) {
        String actorUserId = resolverActorUserId(userId, adminUserId);
        return ResponseEntity.ok(instanciaService.obtenerHistorial(actorUserId, id));
    }

    private EstadoInstancia parseEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }

        try {
            return EstadoInstancia.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "estado invalido. Valores: EN_CURSO, PAUSADA, FINALIZADA, CANCELADA");
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
