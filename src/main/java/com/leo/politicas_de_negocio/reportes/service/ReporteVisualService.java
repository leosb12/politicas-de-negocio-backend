package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteVisualService {

    private final ReporteVisualPromptService promptService;
    private final ReporteVisualQueryService queryService;
    private final ReporteVisualMapper visualMapper;
    private final ReporteCatalogoService catalogoService;
    private final UsuarioRepository usuarioRepository;
    private final PoliticaNegocioRepository politicaNegocioRepository;

    public ReporteVisualDTO generarReporteVisual(String prompt, String usuarioId) {
        return generarReporteVisual(prompt, usuarioId, false);
    }

    public ReporteVisualDTO generarReporteVisual(String prompt, String usuarioId, Boolean iaPlus) {
        log.info("Generando reporte visual inteligente para usuario: {} con prompt: '{}', iaPlus: {}", usuarioId, prompt, iaPlus);

        List<String> usuariosReales = new ArrayList<>();
        List<String> politicasReales = new ArrayList<>();

        if (Boolean.TRUE.equals(iaPlus)) {
            try {
                usuariosReales = usuarioRepository.findAll().stream()
                        .filter(u -> "USUARIO".equalsIgnoreCase(u.getRol()))
                        .map(Usuario::getNombre)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

                politicasReales = politicaNegocioRepository.findAll().stream()
                        .map(PoliticaNegocio::getNombre)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
            } catch (Exception e) {
                log.warn("Error al recuperar usuarios/politicas reales para IA+: ", e);
            }
        }

        // 1. Interpretar el prompt con el motor IA
        ReporteVisualPromptService.PromptReporteResponse interpretacion = 
                promptService.interpretarPromptVisual(prompt, usuarioId, iaPlus, usuariosReales, politicasReales);

        List<BloqueReporteDTO> bloquesDto = new ArrayList<>();

        // 2. Procesar cada bloque interpretado
        for (ReporteVisualPromptService.PromptBloqueIntent intent : interpretacion.getBloques()) {
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

                // A) Validaciones obligatorias de seguridad y semántica
                if (intent.getEntidadPrincipal() == null || intent.getEntidadPrincipal().trim().isEmpty()) {
                    bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                            blockId,
                            intent.getTitulo(),
                            "La entidad principal no fue especificada para este bloque.",
                            intent.getOrden()
                    ));
                    continue;
                }

                if (!catalogoService.esEntidadPermitida(intent.getEntidadPrincipal())) {
                    bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                            blockId,
                            intent.getTitulo(),
                            "No puedo generar este bloque porque la entidad '" + intent.getEntidadPrincipal() + "' no está en el catálogo de datos permitido.",
                            intent.getOrden()
                    ));
                    continue;
                }

                // Validar filtros
                boolean filtrosValidos = true;
                if (intent.getFiltros() != null) {
                    for (String campoFiltro : intent.getFiltros().keySet()) {
                        if (!catalogoService.esCampoPermitido(intent.getEntidadPrincipal(), campoFiltro)) {
                            bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                                    blockId,
                                    intent.getTitulo(),
                                    "No puedo generar este bloque porque el campo de filtro '" + campoFiltro + "' no existe en la entidad '" + intent.getEntidadPrincipal() + "'.",
                                    intent.getOrden()
                            ));
                            filtrosValidos = false;
                            break;
                        }
                    }
                }
                if (!filtrosValidos) {
                    continue;
                }

                // B) Ejecutar la consulta o utilizar los datos simulados
                ResultadoBloqueReporteDTO datasetDto;
                if (intent.getDatos() != null) {
                    datasetDto = intent.getDatos();
                } else {
                    int limite = intent.getLimite() > 0 ? intent.getLimite() : 10;
                    List<Map> registros = queryService.ejecutarConsultaMetrica(
                            intent.getMetrica(), 
                            intent.getEntidadPrincipal(), 
                            intent.getFiltros(), 
                            limite,
                            iaPlus
                    );

                    // C) Mapear registros al DTO del dataset
                    datasetDto = visualMapper.mapear(intent.getTipo(), registros);
                }

                // D) Construir configuración de metadatos del gráfico
                ConfiguracionGraficoDTO configDto = construirConfiguracionGrafico(intent, datasetDto);

                // E) Crear el bloque DTO
                BloqueReporteDTO bloque = BloqueReporteDTO.builder()
                        .id(blockId)
                        .tipo(intent.getTipo())
                        .titulo(intent.getTitulo())
                        .orden(intent.getOrden())
                        .posicion(intent.getOrden())
                        .datos(datasetDto)
                        .dataset(datasetDto) // Alias para asegurar mapeo dual en frontend
                        .configuracion(configDto)
                        .build();

                bloquesDto.add(bloque);

            } catch (IllegalArgumentException e) {
                log.warn("Error de validación al generar bloque: ", e);
                bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                        blockId,
                        intent.getTitulo() != null ? intent.getTitulo() : "Bloque de Reporte",
                        e.getMessage(),
                        intent.getOrden()
                ));
            } catch (Exception e) {
                log.error("Error inesperado al generar bloque: ", e);
                bloquesDto.add(BloqueReporteDTO.createErrorBlock(
                        blockId,
                        intent.getTitulo() != null ? intent.getTitulo() : "Bloque de Reporte",
                        "Error interno al procesar los datos para este bloque.",
                        intent.getOrden()
                ));
            }
        }

        // 3. Retornar el reporte completo
        return ReporteVisualDTO.builder()
                .titulo(interpretacion.getTitulo() != null ? interpretacion.getTitulo() : "Reporte Inteligente Personalizado")
                .descripcion(interpretacion.getDescripcion() != null ? interpretacion.getDescripcion() : "Reporte generado según instrucción natural.")
                .promptOriginal(prompt)
                .fechaGeneracion(LocalDateTime.now())
                .bloques(bloquesDto)
                .asistido(iaPlus)
                .build();
    }

    private ConfiguracionGraficoDTO construirConfiguracionGrafico(ReporteVisualPromptService.PromptBloqueIntent intent, ResultadoBloqueReporteDTO dataset) {
        String xKey = "Categoría";
        String yKey = "Valor";
        String desc = "Distribución de datos para " + intent.getTitulo();

        if ("kpi".equalsIgnoreCase(intent.getTipo())) {
            xKey = dataset.getLabels().isEmpty() ? "Métrica" : dataset.getLabels().get(0);
            yKey = "total";
            desc = "Valor principal para: " + intent.getTitulo();
        } else if ("table".equalsIgnoreCase(intent.getTipo()) || "matrix".equalsIgnoreCase(intent.getTipo())) {
            xKey = "columnas";
            yKey = "filas";
            desc = "Detalle tabular: " + intent.getTitulo();
        } else {
            // Para gráficos, las keys se corresponden con el label y el value mapeados
            if (dataset.getLabels() != null && !dataset.getLabels().isEmpty()) {
                xKey = "nombre"; // genérico
            }
            if (dataset.getValues() != null && !dataset.getValues().isEmpty()) {
                yKey = "cantidad"; // genérico
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
