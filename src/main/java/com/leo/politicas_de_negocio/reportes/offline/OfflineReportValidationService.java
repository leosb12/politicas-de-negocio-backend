package com.leo.politicas_de_negocio.reportes.offline;

import com.leo.politicas_de_negocio.reportes.service.ReporteCatalogoService;
import com.leo.politicas_de_negocio.reportes.service.ReporteVisualPromptService.PromptBloqueIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineReportValidationService {

    private final ReporteCatalogoService catalogoService;

    public String validarBloqueOffline(PromptBloqueIntent intent) {
        if ("error".equalsIgnoreCase(intent.getTipo())) {
            return null; // Los bloques de error explícitos de la IA se manejan directamente
        }

        if (intent.getEntidadPrincipal() != null && "pagos".equalsIgnoreCase(intent.getEntidadPrincipal())) {
            return "No se permiten reportes de pagos, cobros o recaudación en modo offline.";
        }

        if (intent.getMetrica() != null && (intent.getMetrica().toLowerCase().contains("pago") || intent.getMetrica().toLowerCase().contains("recaud"))) {
            return "No se permiten reportes de pagos, cobros o recaudación en modo offline.";
        }

        if (intent.getEntidadPrincipal() == null || intent.getEntidadPrincipal().trim().isEmpty()) {
            return "La entidad principal no fue especificada para este bloque.";
        }

        if (!catalogoService.esEntidadPermitida(intent.getEntidadPrincipal())) {
            return "La entidad '" + intent.getEntidadPrincipal() + "' no está en el catálogo de datos permitido.";
        }

        // Validar filtros
        if (intent.getFiltros() != null) {
            for (String campoFiltro : intent.getFiltros().keySet()) {
                if ("mesActual".equals(campoFiltro)) {
                    continue; // Filtro temporal sintético permitido
                }
                if (!catalogoService.esCampoPermitido(intent.getEntidadPrincipal(), campoFiltro)) {
                    return "El campo de filtro '" + campoFiltro + "' no existe en la entidad '" + intent.getEntidadPrincipal() + "'.";
                }
            }
        }

        // Validar que la métrica esté presente
        if (intent.getMetrica() == null || intent.getMetrica().trim().isEmpty()) {
            return "La métrica no fue especificada para este bloque.";
        }

        if (intent.getLimite() > 1000) {
            return "El límite máximo de registros en modo offline es 1000.";
        }

        return null; // Todo válido
    }

    public void validarResultadoOffline(com.leo.politicas_de_negocio.reportes.dto.ReporteVisualDTO dto) {
        if (dto == null || dto.getBloques() == null) {
            return;
        }
        for (com.leo.politicas_de_negocio.reportes.dto.BloqueReporteDTO bloque : dto.getBloques()) {
            com.leo.politicas_de_negocio.reportes.dto.ResultadoBloqueReporteDTO data = bloque.getDataset();
            if (data == null) {
                data = bloque.getDatos();
            }
            if (data != null && data.getLabels() != null) {
                for (String label : data.getLabels()) {
                    if (label != null && label.matches("^\\d{4}-\\d{2}$")) {
                        if (!label.equals("2026-04") && !label.equals("2026-05") && !label.equals("2026-06")) {
                            log.error("VALIDATION_ERROR: Mes '{}' fuera de rango (abril-junio 2026) en el bloque '{}'.", label, bloque.getTitulo());
                            throw new com.leo.politicas_de_negocio.shared.exception.ApiException(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    "Error de validación offline: se detectaron datos del mes " + label + " fuera del rango permitido (abril-junio 2026)."
                            );
                        }
                    }
                }
            }
        }
    }
}
