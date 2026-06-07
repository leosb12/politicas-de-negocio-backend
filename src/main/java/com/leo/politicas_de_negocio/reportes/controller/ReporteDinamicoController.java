package com.leo.politicas_de_negocio.reportes.controller;

import com.leo.politicas_de_negocio.reportes.dto.AudioRequestDto;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteRequestDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.service.ReporteCatalogoService;
import com.leo.politicas_de_negocio.reportes.service.ReporteDinamicoService;
import com.leo.politicas_de_negocio.reportes.service.ReporteExportadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/reportes")
@RequiredArgsConstructor
public class ReporteDinamicoController {

    private final ReporteDinamicoService dinamicoService;
    private final ReporteCatalogoService catalogoService;
    private final ReporteExportadorService exportadorService;

    @GetMapping("/catalogo")
    public ResponseEntity<Map<String, List<String>>> getCatalogo() {
        return ResponseEntity.ok(catalogoService.getCatalogo());
    }

    @PostMapping("/interpretar")
    public ResponseEntity<ReporteResponseDto> interpretar(@RequestBody ReporteRequestDto request, @RequestHeader("X-Admin-User-Id") String userId) {
        return ResponseEntity.ok(dinamicoService.interpretar(request, userId, "ADMIN"));
    }

    @PostMapping("/preview")
    public ResponseEntity<PreviewResponseDto> generarPreview(@RequestBody ReporteResponseDto definicion, @RequestHeader("X-Admin-User-Id") String userId, @RequestParam(required = false, defaultValue = "") String textoOriginal) {
        return ResponseEntity.ok(dinamicoService.generarPreview(definicion, textoOriginal, userId));
    }

    @PostMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@RequestBody PreviewResponseDto previewResponse, @RequestParam String formato) {
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
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte" + extension + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    @PostMapping("/audio")
    public ResponseEntity<ReporteResponseDto> audio(@RequestBody AudioRequestDto req, @RequestHeader("X-Admin-User-Id") String userId) {
        // En una implementacion real, enviariamos a transcribir
        ReporteRequestDto textoReq = new ReporteRequestDto();
        textoReq.setTexto("quiero ver la politica mas usada este mes en excel"); // Mock
        return ResponseEntity.ok(dinamicoService.interpretar(textoReq, userId, "ADMIN"));
    }

    @GetMapping("/historial")
    public ResponseEntity<?> getHistorial() {
        return ResponseEntity.ok(dinamicoService.getHistorial());
    }
}
