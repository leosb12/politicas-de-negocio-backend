package com.leo.politicas_de_negocio.reportes.offline;

import com.leo.politicas_de_negocio.reportes.dto.BloqueReporteDTO;
import com.leo.politicas_de_negocio.reportes.dto.ConfiguracionGraficoDTO;
import com.leo.politicas_de_negocio.reportes.dto.ReporteVisualDTO;
import com.leo.politicas_de_negocio.reportes.dto.ResultadoBloqueReporteDTO;
import com.leo.politicas_de_negocio.reportes.service.ReporteVisualMapper;
import com.leo.politicas_de_negocio.reportes.service.ReporteVisualPromptService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineDynamicReportFacade {

    private final OfflineReportLocalDataService localDataService;
    private final OfflineDeepLearningDynamicReportService offlineDeepLearningService;
    private final LocalDynamicReportEngine localReportEngine;
    private final OfflineReportValidationService validationService;
    private final ReporteVisualMapper visualMapper;
    private final OfflineReportSyntheticDataGenerator syntheticDataGenerator;

    @Value("${offline.reports.simulation.enabled:false}")
    private boolean simulationEnabled;

    @Value("${offline.reports.simulation.min-tramites:80}")
    private int minTramites;

    @SuppressWarnings("unchecked")
    public ReporteVisualDTO generarReporteOffline(String prompt, String usuarioId, Boolean iaPlus) {
        log.info("OFFLINE_REPORT_REQUEST_RECEIVED: Iniciando generación de reporte offline para el prompt: '{}'", prompt);

        // 1. Cargar snapshot local
        Map<String, Object> snapshot = localDataService.loadSnapshot();

        // 2. Validar existencia de datos
        if (snapshot == null || snapshot.isEmpty()) {
            log.warn("OFFLINE_SNAPSHOT_NOT_FOUND: No se encontró el archivo snapshot offline o está vacío.");
            throw new ApiException(HttpStatus.BAD_REQUEST, 
                    "No existen datos offline sincronizados suficientes para generar este reporte. Sincroniza los datos cuando estés online.");
        }

        log.info("OFFLINE_SNAPSHOT_FOUND: Snapshot offline cargado con éxito. Validando metadatos.");
        Map<String, Object> metadata = (Map<String, Object>) snapshot.get("metadata");
        if (metadata != null) {
            log.info("Metadata del snapshot: generatedAt={}, source={}, mode={}", 
                    metadata.get("generatedAt"), metadata.get("source"), metadata.get("mode"));
        }

        // Determinar modo de datos y aplicar simulación si corresponde
        List<Map<String, Object>> realInstances = (List<Map<String, Object>>) snapshot.get("instancias_politica");
        int realCount = realInstances != null ? realInstances.size() : 0;

        String modeMessage;
        if (realCount >= minTramites) {
            modeMessage = "Modo Offline Activo: reporte generado con Deep Learning local y datos sincronizados.";
            log.info("Modo de Datos: OFFLINE_REAL_DATA_DEEP_LEARNING_LOCAL (Conteo real: {})", realCount);
        } else {
            if (simulationEnabled) {
                if (realCount > 0) {
                    modeMessage = "Modo Offline Activo: reporte generado con datos sincronizados parciales.";
                    log.info("Modo de Datos: OFFLINE_PARTIAL_DATA_DEEP_LEARNING_LOCAL (Conteo real: {})", realCount);
                } else {
                    modeMessage = "Modo Demo Offline: reporte generado con datos operativos simulados usando catálogos reales sincronizados.";
                    log.info("Modo de Datos: OFFLINE_SIMULATED_FALLBACK");
                }
                snapshot = syntheticDataGenerator.generateSimulationData(snapshot, realCount);
            } else {
                log.warn("OFFLINE_INSUFFICIENT_DATA: Datos insuficientes ({}) y simulación deshabilitada.", realCount);
                throw new ApiException(HttpStatus.BAD_REQUEST, 
                        "No existen datos offline suficientes para generar este reporte. Sincroniza datos estando online.");
            }
        }

        // 3. Inicializar pipeline de IA (Llamar a FastAPI local)
        log.info("OFFLINE_REPORT_PIPELINE_READY: Procesando llamada paralela al microservicio IA local.");
        ReporteVisualPromptService.PromptReporteResponse interpretacion = 
                offlineDeepLearningService.interpretarOffline(prompt, usuarioId, iaPlus);

        // 4. Procesar y calcular cada bloque de manera offline
        List<BloqueReporteDTO> bloquesDto = new ArrayList<>();
        if (interpretacion.getBloques() != null) {
            List<ReporteVisualPromptService.PromptBloqueIntent> intents = interpretacion.getBloques();
            if (intents.size() > 5) {
                log.info("Limitando bloques de reporte a un máximo de 5 en modo offline (recibidos: {})", intents.size());
                intents = intents.subList(0, 5);
            }

            for (ReporteVisualPromptService.PromptBloqueIntent intent : intents) {
                String blockId = "bloque_" + intent.getOrden();
                
                try {
                    // Caso especial de bloque de error generado por la IA
                    if ("error".equalsIgnoreCase(intent.getTipo())) {
                        bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                                blockId, 
                                intent.getTitulo(), 
                                intent.getIntencion(), 
                                intent.getOrden()
                        ));
                        continue;
                    }

                    // A) Validaciones obligatorias de seguridad (omitir si iaPlus está activado y simulationEnabled es true)
                    boolean bypassValidation = Boolean.TRUE.equals(iaPlus) && simulationEnabled;
                    if (!bypassValidation) {
                        String errorMsg = validationService.validarBloqueOffline(intent);
                        if (errorMsg != null) {
                            bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                                     blockId,
                                     intent.getTitulo(),
                                     errorMsg,
                                     intent.getOrden()
                            ));
                            continue;
                        }
                    }

                    // B) Cargar datos: Simulación (si aplica y está activa) o motor local
                    ResultadoBloqueReporteDTO datasetDto;
                    if (Boolean.TRUE.equals(iaPlus) && simulationEnabled && intent.getDatos() != null) {
                        log.info("Usando datos simulados para el bloque '{}' en modo IA+ demo", intent.getTitulo());
                        datasetDto = intent.getDatos();
                    } else {
                        int limite = intent.getLimite() > 0 ? intent.getLimite() : 10;
                        List<Map> registros = localReportEngine.ejecutarConsultaMetricaLocal(
                                intent.getMetrica(),
                                intent.getEntidadPrincipal(),
                                intent.getFiltros(),
                                limite,
                                snapshot
                        );
                        datasetDto = visualMapper.mapear(intent.getTipo(), registros);
                    }

                    // C) Construir configuración de metadatos del gráfico
                    ConfiguracionGraficoDTO configDto = construirConfiguracionGrafico(intent, datasetDto);

                    // D) Crear bloque DTO final
                    BloqueReporteDTO bloque = BloqueReporteDTO.builder()
                            .id(blockId)
                            .tipo(intent.getTipo())
                            .titulo(intent.getTitulo())
                            .orden(intent.getOrden())
                            .posicion(intent.getOrden())
                            .datos(datasetDto)
                            .dataset(datasetDto)
                            .configuracion(configDto)
                            .build();

                    bloquesDto.add(bloque);

                } catch (Exception e) {
                    log.error("Error al generar bloque offline '{}': {}", intent.getTitulo(), e.getMessage(), e);
                    bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                            blockId,
                            intent.getTitulo() != null ? intent.getTitulo() : "Bloque de Reporte",
                            "Error interno al calcular datos locales para este bloque.",
                            intent.getOrden()
                    ));
                }
            }
        }

        String tituloReporte = interpretacion.getTitulo() != null ? interpretacion.getTitulo() : "Reporte Inteligente";
        String descReporte = interpretacion.getDescripcion() != null ? interpretacion.getDescripcion() : "Reporte generado en modo offline mediante procesamiento local.";
        descReporte += " (Período analizado: abril 2026 - junio 2026)";

        ReporteVisualDTO dto = ReporteVisualDTO.builder()
                .titulo(tituloReporte)
                .descripcion(descReporte)
                .promptOriginal(prompt)
                .fechaGeneracion(LocalDateTime.now())
                .bloques(bloquesDto)
                .asistido(iaPlus)
                .offlineMessage(modeMessage)
                .build();

        validationService.validarResultadoOffline(dto);

        return dto;
    }

    private ConfiguracionGraficoDTO construirConfiguracionGrafico(ReporteVisualPromptService.PromptBloqueIntent intent, ResultadoBloqueReporteDTO dataset) {
        String xKey = "Categoría";
        String yKey = "Valor";
        String desc = "Distribución de datos para " + intent.getTitulo();

        if (dataset == null) {
            return ConfiguracionGraficoDTO.builder()
                    .xKey(xKey)
                    .yKey(yKey)
                    .descripcion(desc)
                    .build();
        }

        if ("kpi".equalsIgnoreCase(intent.getTipo())) {
            xKey = (dataset.getLabels() == null || dataset.getLabels().isEmpty()) ? "Métrica" : dataset.getLabels().get(0);
            yKey = "total";
            desc = "Valor principal para: " + intent.getTitulo();
        } else if ("table".equalsIgnoreCase(intent.getTipo()) || "matrix".equalsIgnoreCase(intent.getTipo())) {
            xKey = "columnas";
            yKey = "filas";
            desc = "Detalle tabular: " + intent.getTitulo();
        } else {
            if (dataset.getLabels() != null && !dataset.getLabels().isEmpty()) {
                xKey = "nombre";
            }
            if (dataset.getValues() != null && !dataset.getValues().isEmpty()) {
                yKey = "cantidad";
            }
            desc = "Gráfico de " + intent.getTipo() + " representando " + intent.getTitulo();
        }

        return ConfiguracionGraficoDTO.builder()
                .xKey(xKey)
                .yKey(yKey)
                .descripcion(desc)
                .build();
    }
}

