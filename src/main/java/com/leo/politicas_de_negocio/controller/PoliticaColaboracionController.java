package com.leo.politicas_de_negocio.controller;

import com.leo.politicas_de_negocio.dto.politica.colaboracion.NodoBloqueoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.ColaboracionEstadoResponse;
import com.leo.politicas_de_negocio.dto.politica.colaboracion.PresenciaPoliticaResponse;
import com.leo.politicas_de_negocio.model.colaboracion.EventoColaboracionAplicado;
import com.leo.politicas_de_negocio.service.PoliticaColaboracionService;
import com.leo.politicas_de_negocio.service.PoliticaPresenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/politicas/{politicaId}/colaboracion")
@RequiredArgsConstructor
public class PoliticaColaboracionController {

    private final PoliticaColaboracionService colaboracionService;
    private final PoliticaPresenciaService presenciaService;

    @GetMapping("/estado")
    public ResponseEntity<ColaboracionEstadoResponse> obtenerEstadoActual(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String politicaId
    ) {
        return ResponseEntity.ok(colaboracionService.obtenerEstadoActual(adminUserId, politicaId));
    }

    @GetMapping("/historial")
    public ResponseEntity<List<EventoColaboracionAplicado>> obtenerHistorialReciente(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String politicaId
    ) {
        return ResponseEntity.ok(colaboracionService.obtenerHistorialReciente(adminUserId, politicaId));
    }

    @GetMapping("/presencia")
    public ResponseEntity<PresenciaPoliticaResponse> obtenerPresenciaActual(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String politicaId
    ) {
        return ResponseEntity.ok(presenciaService.obtenerPresenciaActual(adminUserId, politicaId));
    }

    @GetMapping("/nodos-bloqueados")
    public ResponseEntity<List<NodoBloqueoResponse>> obtenerBloqueosActivos(
            @RequestHeader("X-Admin-User-Id") String adminUserId,
            @PathVariable String politicaId
    ) {
        return ResponseEntity.ok(presenciaService.obtenerBloqueosActivos(adminUserId, politicaId));
    }
}
