package com.leo.politicas_de_negocio.politicas.controller;

import com.leo.politicas_de_negocio.politicas.dto.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.service.PoliticaNegocioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/politicas")
@RequiredArgsConstructor
public class PoliticaNegocioController {

    private final PoliticaNegocioService service;

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

    @GetMapping("/{id}")
    public ResponseEntity<PoliticaNegocio> obtenerPorId(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String id) {
        return ResponseEntity.ok(service.obtenerPorId(adminUserId, id));
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
            service.actualizarNombreDescripcion(adminUserId, id, request.getNombre(), request.getDescripcion())
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
}
