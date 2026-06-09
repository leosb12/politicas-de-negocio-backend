package com.leo.politicas_de_negocio.reportes.controller;

import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualDTO;
import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualRequestDTO;
import com.leo.politicas_de_negocio.reportes.service.ReporteVisualService;
import com.leo.politicas_de_negocio.analiticas.service.SystemAuditService;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
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
    private final SystemAuditService systemAuditService;
    private final UsuarioRepository usuarioRepository;

    @PostMapping("/generar")
    public ResponseEntity<ReporteVisualDTO> generarReporte(
            @RequestBody ReporteVisualRequestDTO request,
            @RequestHeader(value = "X-Admin-User-Id", required = false, defaultValue = "system") String headerUserId) {
        
        log.info("REST Request recibido para generar reporte visual inteligente");
        
        String finalUserId = request.getUsuarioId() != null && !request.getUsuarioId().isEmpty()
                ? request.getUsuarioId() 
                : headerUserId;

        ReporteVisualDTO response = visualService.generarReporteVisual(request.getPrompt(), finalUserId, request.getIaPlus());

        // Auditoría del sistema: Registrar la generación del reporte
        try {
            Usuario usuario = usuarioRepository.findById(finalUserId).orElse(null);
            String nombre = "Sistema";
            String correo = "correo@sistema.com";
            String rol = "ADMIN";
            if (usuario != null) {
                nombre = usuario.getNombre();
                correo = usuario.getCorreo();
                rol = usuario.getRol();
            }

            String promptLower = request.getPrompt().toLowerCase();
            String formato = "PANTALLA";
            if (promptLower.contains("excel") || promptLower.contains("xlsx") || promptLower.contains("xls") || promptLower.contains("csv") || promptLower.contains("planilla") || promptLower.contains("cálculo") || promptLower.contains("calculo")) {
                formato = "EXCEL";
            } else if (promptLower.contains("pdf")) {
                formato = "PDF";
            } else if (promptLower.contains("word") || promptLower.contains("docx") || promptLower.contains("doc") || promptLower.contains("documento")) {
                formato = "WORD";
            } else if (promptLower.contains("powerpoint") || promptLower.contains("power point") || promptLower.contains("pptx") || promptLower.contains("ppt") || promptLower.contains("presentación") || promptLower.contains("presentacion")) {
                formato = "POWERPOINT";
            }

            String detalle = String.format("Generó reporte inteligente: '%s' (Formato: %s)", request.getPrompt(), formato);
            systemAuditService.log(finalUserId, nombre, correo, rol, "REPORTE_GENERACION", detalle);
        } catch (Exception e) {
            log.error("Error al registrar auditoría de generación de reporte: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}
