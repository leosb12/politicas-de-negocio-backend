package com.leo.politicas_de_negocio.reportes.controller;

import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualDTO;
import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualRequestDTO;
import com.leo.politicas_de_negocio.reportes.service.ReporteVisualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reportes-visuales")
@RequiredArgsConstructor
@Slf4j
public class ReporteVisualController {

    private final ReporteVisualService visualService;

    @PostMapping("/generar")
    public ResponseEntity<ReporteVisualDTO> generarReporte(
            @RequestBody ReporteVisualRequestDTO request,
            @RequestHeader(value = "X-Admin-User-Id", required = false, defaultValue = "system") String headerUserId) {
        
        log.info("REST Request recibido para generar reporte visual inteligente");
        
        String finalUserId = request.getUsuarioId() != null && !request.getUsuarioId().isEmpty()
                ? request.getUsuarioId() 
                : headerUserId;

        ReporteVisualDTO response = visualService.generarReporteVisual(request.getPrompt(), finalUserId, request.getIaPlus());
        return ResponseEntity.ok(response);
    }
}
