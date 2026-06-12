package com.leo.politicas_de_negocio.reportes.offline;

import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualDTO;
import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/reportes-visuales")
@RequiredArgsConstructor
@Slf4j
public class OfflineReportController {

    private final OfflineReportSyncDataService syncDataService;
    private final OfflineDynamicReportFacade offlineFacade;

    @PostMapping("/sync-offline")
    public ResponseEntity<Map<String, Object>> sincronizarOffline() {
        log.info("REST Request recibido para sincronizar datos offline para reportes");
        Map<String, Object> snapshot = syncDataService.sincronizarDatosOffline();
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/generar-offline")
    public ResponseEntity<ReporteVisualDTO> generarOffline(
            @RequestBody ReporteVisualRequestDTO request,
            @RequestHeader(value = "X-Admin-User-Id", required = false, defaultValue = "system") String headerUserId) {
        log.info("REST Request recibido para generar reporte visual inteligente offline. Prompt: '{}'", request.getPrompt());
        
        String finalUserId = request.getUsuarioId() != null && !request.getUsuarioId().isEmpty()
                ? request.getUsuarioId() 
                : headerUserId;

        ReporteVisualDTO response = offlineFacade.generarReporteOffline(request.getPrompt(), finalUserId, request.getIaPlus());
        return ResponseEntity.ok(response);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(OfflineLocalAiUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleLocalAiUnavailable(OfflineLocalAiUnavailableException ex) {
        log.info("OFFLINE_BROWSER_FALLBACK_ALLOWED: Retornando respuesta controlada para fallback del navegador ante error en ia-deep-learning-service.");
        Map<String, Object> body = Map.of(
            "code", "LOCAL_IA_UNAVAILABLE",
            "message", "El motor de Deep Learning local no está disponible. Puede usarse fallback simple del navegador.",
            "fallbackAllowed", true,
            "mode", "OFFLINE_BROWSER_SIMPLE_FALLBACK_REQUIRED"
        );
        return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}

