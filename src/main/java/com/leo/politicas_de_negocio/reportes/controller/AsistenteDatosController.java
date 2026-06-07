package com.leo.politicas_de_negocio.reportes.controller;

import com.leo.politicas_de_negocio.reportes.dto.AsistenteDatosRequestDto;
import com.leo.politicas_de_negocio.reportes.dto.AsistenteDatosResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import com.leo.politicas_de_negocio.reportes.service.AsistenteDatosService;
import com.leo.politicas_de_negocio.reportes.service.ReporteCatalogoService;
import com.leo.politicas_de_negocio.reportes.service.ReporteExportadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller del Asistente de Datos Inteligente.
 * Permite realizar preguntas libres sobre los datos del sistema.
 */
@RestController
@RequestMapping("/api/admin/asistente-datos")
@RequiredArgsConstructor
public class AsistenteDatosController {

    private final AsistenteDatosService asistenteDatosService;
    private final ReporteCatalogoService catalogoService;
    private final ReporteExportadorService exportadorService;

    /**
     * Procesa una pregunta libre y retorna datos + respuesta natural.
     * Flujo completo: interpreta → valida → consulta → responde.
     */
    @PostMapping("/preguntar")
    public ResponseEntity<AsistenteDatosResponseDto> preguntar(
            @RequestBody AsistenteDatosRequestDto request,
            @RequestHeader("X-Admin-User-Id") String userId) {
        return ResponseEntity.ok(asistenteDatosService.preguntar(request, userId, "ADMIN"));
    }

    /**
     * Solo genera el plan de consulta sin ejecutar.
     * Útil para modo avanzado donde el admin revisa antes de ejecutar.
     */
    @PostMapping("/planificar")
    public ResponseEntity<Map<String, Object>> planificar(
            @RequestBody AsistenteDatosRequestDto request,
            @RequestHeader("X-Admin-User-Id") String userId) {
        return ResponseEntity.ok(asistenteDatosService.planificar(request, userId, "ADMIN"));
    }

    /**
     * Ejecuta un plan ya generado y validado.
     */
    @PostMapping("/ejecutar-plan")
    public ResponseEntity<AsistenteDatosResponseDto> ejecutarPlan(
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Admin-User-Id") String userId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) body.getOrDefault("plan", body);
        String textoOriginal = (String) body.getOrDefault("textoOriginal", "");
        return ResponseEntity.ok(asistenteDatosService.ejecutarPlan(plan, textoOriginal, userId));
    }

    /**
     * Catálogo de entidades y campos disponibles para consulta.
     */
    @GetMapping("/catalogo")
    public ResponseEntity<Map<String, List<String>>> getCatalogo() {
        return ResponseEntity.ok(catalogoService.getCatalogo());
    }

    /**
     * Exportar datos del asistente a un formato específico.
     */
    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestBody PreviewResponseDto previewResponse,
            @RequestParam String formato) {
        byte[] bytes;
        String contentType;
        String extension;

        switch (formato.toLowerCase()) {
            case "excel":
                bytes = exportadorService.exportarExcel(previewResponse);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                extension = ".xlsx";
                break;
            case "pdf":
                bytes = exportadorService.exportarPdf(previewResponse);
                contentType = MediaType.APPLICATION_PDF_VALUE;
                extension = ".pdf";
                break;
            case "word":
                bytes = exportadorService.exportarWord(previewResponse);
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                extension = ".docx";
                break;
            default:
                throw new IllegalArgumentException("Formato no soportado: " + formato);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"asistente_datos" + extension + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
