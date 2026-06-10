package com.leo.politicas_de_negocio.reportes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteRequestDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.model.ReporteGenerado;
import com.leo.politicas_de_negocio.reportes.repository.ReporteGeneradoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteDinamicoService {

    private final ReporteMongoAggregationBuilder aggregationBuilder;
    private final ReporteJsonNormalizer jsonNormalizer;
    private final ReporteCatalogoService catalogoService;
    private final ReporteGeneradoRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final MongoTemplate mongoTemplate;
    private final ReporteUniversalResolverService universalResolverService;
    private final ReporteCompuestoService compuestoService;
    private final ReporteCampoResolver campoResolver;
    private final com.leo.politicas_de_negocio.analiticas.service.AnalyticsService analyticsService;
    private final ReporteOutputContractService outputContractService;
    private final ReporteAsistidoService asistidoService;

    @Value("${app.ia.deep-learning-url:http://localhost:8010}")
    private String iaServiceUrl;

    /**
     * Interpreta una consulta libre enviándola al Motor IA (FastAPI).
     * La IA interpreta y genera un plan; este servicio valida y orquesta.
     */
    public ReporteResponseDto interpretar(ReporteRequestDto request, String usuarioId, String rol) {
        try {
            String url = iaServiceUrl + "/api/ia/reportes/interpretar";
            
            Map<String, String> body = new HashMap<>();
            body.put("texto", request.getTexto());
            body.put("usuarioId", usuarioId);
            body.put("rol", rol);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ReporteResponseDto interpretacion = restTemplate.postForObject(url, entity, ReporteResponseDto.class);
            return interpretacion;
        } catch (Exception e) {
            log.error("Error al comunicarse con el Motor IA: ", e);
            // Retornar respuesta de fallback en vez de lanzar excepción
            ReporteResponseDto fallback = new ReporteResponseDto();
            fallback.setRequiereAclaracion(true);
            fallback.setPreguntaAclaratoria(
                "El servicio de interpretación IA no está disponible en este momento. " +
                "Por favor, verifica que el servicio ia-deep-learning-service esté ejecutándose."
            );
            fallback.setMotor("MOTOR_FALLBACK");
            fallback.setConfianza(0.0);
            return fallback;
        }
    }

    /**
     * Genera vista previa ejecutando la consulta contra MongoDB.
     * Registra el resultado en el historial de auditoría.
     */
    public PreviewResponseDto generarPreview(ReporteResponseDto definicion, String originalText, String usuarioId) {
        return generarPreview(definicion, originalText, usuarioId, false);
    }

    public PreviewResponseDto generarPreview(ReporteResponseDto definicion, String originalText, String usuarioId, Boolean iaPlus) {
        if (Boolean.TRUE.equals(iaPlus)) {
            List<String> campos = (definicion != null && definicion.getCampos() != null) ? definicion.getCampos() : new ArrayList<>();
            return asistidoService.generarVistaAsistida(originalText, definicion, "Modo Asistencia IA+ activa", campos, new ArrayList<>());
        }

        if (definicion != null && "cuellos_botella".equals(definicion.getIntencionDetectada())) {
            return generarReporteCuellosBotella(usuarioId, definicion, originalText);
        }

        // Fallback: detectar intención cuellos_botella por palabras clave si la IA no lo hizo
        if (originalText != null && definicion != null && !"cuellos_botella".equals(definicion.getIntencionDetectada())) {
            String textLc = originalText.toLowerCase();
            boolean esCuello = textLc.contains("cuello de botella") || textLc.contains("cuellos de botella")
                    || textLc.contains("bottleneck") || textLc.contains("proceso trabado")
                    || textLc.contains("nodo lento") || textLc.contains("etapa lenta")
                    || textLc.contains("funcionario saturado")
                    || (textLc.contains("demora") && textLc.contains("recomendaci"))
                    || (textLc.contains("demora") && (textLc.contains(" ia") || textLc.contains("inteligencia")))
                    || (textLc.contains("nodo") && textLc.contains("demora") && textLc.contains("recomendaci"))
                    || (textLc.contains("etapa") && textLc.contains("demora") && textLc.contains("recomendaci"));
            if (esCuello) {
                definicion.setIntencionDetectada("cuellos_botella");
                return generarReporteCuellosBotella(usuarioId, definicion, originalText);
            }
        }

        // Build contract & repair plan first
        ReporteOutputContractService.OutputContract contract = outputContractService.buildContract(originalText, definicion);
        outputContractService.repairPlan(definicion, contract);

        try {
            // Resolver y reparar el plan con el resolutor universal
            universalResolverService.resolverPlan(definicion, originalText);
        } catch (IllegalArgumentException e) {
            log.warn("Error al resolver plan universalmente: {}", e.getMessage());
            if (Boolean.TRUE.equals(iaPlus)) {
                return asistidoService.generarVistaAsistida(originalText, definicion, e.getMessage(), definicion.getCampos(), new ArrayList<>());
            }
            PreviewResponseDto errorResponse = new PreviewResponseDto();
            errorResponse.setInterpretacion(definicion);
            errorResponse.setError("CAMPO_NO_RESOLUBLE");
            errorResponse.setMensaje(e.getMessage());
            errorResponse.setSugerencias(Arrays.asList(
                "Verifica que el campo solicitado exista en el catálogo o sea derivado válido.",
                "Verifica que exista una ruta de relación en el grafo hacia la entidad destino."
            ));
            errorResponse.setFilas(new java.util.ArrayList<>());
            errorResponse.setColumnas(new java.util.ArrayList<>());
            errorResponse.setTotal(0);
            return errorResponse;
        }

        if (definicion.getRequiereAclaracion() != null && definicion.getRequiereAclaracion()) {
            PreviewResponseDto response = new PreviewResponseDto();
            response.setInterpretacion(definicion);
            response.setFilas(new java.util.ArrayList<>());
            response.setColumnas(new java.util.ArrayList<>());
            response.setTotal(0);
            response.setMensaje(definicion.getPreguntaAclaratoria());
            response.setSugerencias(definicion.getOpcionesSugeridas());
            return response;
        }

        // Si es resumen ejecutivo, generar reporte compuesto
        if ("resumen_ejecutivo".equalsIgnoreCase(definicion.getIntencionDetectada()) || 
            "resumen_ejecutivo".equalsIgnoreCase(definicion.getVisualizacion())) {
            PreviewResponseDto response = new PreviewResponseDto();
            response.setInterpretacion(definicion);
            response.setReporteCompuesto(compuestoService.generarResumenEjecutivoConUsuario(usuarioId));
            response.setFilas(new java.util.ArrayList<>());
            response.setColumnas(new java.util.ArrayList<>());
            response.setTotal(0);
            response.setMensaje("Resumen ejecutivo generado con éxito.");
            return response;
        }

        // Limitar resultados para preview
        if (definicion.getLimite() == null || definicion.getLimite() > 500) {
            definicion.setLimite(500);
        }
        
        // Normalizar JSON de IA a modelo real de BD
        jsonNormalizer.normalizar(definicion, originalText);

        List<Map> resultados = null;
        try {
            resultados = aggregationBuilder.ejecutarConsulta(definicion);
        } catch (IllegalArgumentException e) {
            if (Boolean.TRUE.equals(iaPlus)) {
                return asistidoService.generarVistaAsistida(originalText, definicion, e.getMessage(), definicion.getCampos(), new ArrayList<>());
            }
            PreviewResponseDto errorResponse = new PreviewResponseDto();
            errorResponse.setInterpretacion(definicion);
            errorResponse.setError(e.getMessage().contains("Entidad no permitida") ? "ENTIDAD_NO_REPORTABLE" : "CAMPO_NO_REPORTABLE");
            errorResponse.setMensaje(e.getMessage());
            if (e.getMessage().contains("Entidad no permitida")) {
                errorResponse.setSugerencias(new java.util.ArrayList<>(catalogoService.getCatalogo().keySet()));
            } else {
                errorResponse.setSugerencias(catalogoService.getCatalogo().get(definicion.getEntidadPrincipal()));
            }
            errorResponse.setFilas(new java.util.ArrayList<>());
            errorResponse.setColumnas(new java.util.ArrayList<>());
            errorResponse.setTotal(0);
            return errorResponse;
        }

        List<Map<String, Object>> filas = new java.util.ArrayList<>();
        if (resultados != null) {
            for (Map m : resultados) {
                filas.add((Map<String, Object>) m);
            }
        }

        // Validate output contract
        List<String> contractErrors = outputContractService.validate(filas, contract);
        if (!contractErrors.isEmpty()) {
            log.warn("El reporte falló la validación del contrato de salida: {}. Reintentando reparación y re-ejecución.", contractErrors);
            outputContractService.repairPlan(definicion, contract);
            try {
                universalResolverService.resolverPlan(definicion, originalText);
                jsonNormalizer.normalizar(definicion, originalText);
                resultados = aggregationBuilder.ejecutarConsulta(definicion);
                
                filas = new java.util.ArrayList<>();
                if (resultados != null) {
                    for (Map m : resultados) {
                        filas.add((Map<String, Object>) m);
                    }
                }
                contractErrors = outputContractService.validate(filas, contract);
            } catch (Exception ex) {
                log.error("Error al re-ejecutar plan reparado: ", ex);
            }

            if (!contractErrors.isEmpty()) {
                if (Boolean.TRUE.equals(iaPlus)) {
                    return asistidoService.generarVistaAsistida(originalText, definicion, "Contrato de salida no cumplido: " + contractErrors.get(0), definicion.getCampos(), filas);
                }
                PreviewResponseDto errorResponse = new PreviewResponseDto();
                errorResponse.setInterpretacion(definicion);
                errorResponse.setError("CONTRATO_DE_SALIDA_NO_CUMPLIDO");
                errorResponse.setMensaje("No se pudo cumplir el contrato de salida del reporte: " + contractErrors.get(0));
                errorResponse.setFilas(new java.util.ArrayList<>());
                errorResponse.setColumnas(new java.util.ArrayList<>());
                errorResponse.setTotal(0);
                return errorResponse;
            }
        }
        
        // Registrar en historial de auditoría
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(originalText)
                    .jsonInterpretado(objectMapper.writeValueAsString(definicion))
                    .entidadPrincipal(definicion.getEntidadPrincipal())
                    .intencionDetectada(definicion.getIntencionDetectada())
                    .formatoSalida("pantalla")
                    .visualizacion(definicion.getVisualizacion())
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXITO")
                    .cantidadResultados(resultados.size())
                    .confianzaModelo(definicion.getConfianza())
                    .motorUsado(definicion.getMotor() != null ? definicion.getMotor() : "DESCONOCIDO")
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoría de reporte: ", e);
        }

        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        response.setFilas(filas);
        response.setTotal(filas.size());
        
        if (!filas.isEmpty()) {
            response.setColumnas(new java.util.ArrayList<>(filas.get(0).keySet()));
            response.setMensaje(null);
        } else {
            response.setColumnas(new java.util.ArrayList<>());
            diagnosticarConsultaSinResultados(definicion, response);
            if (Boolean.TRUE.equals(iaPlus)) {
                return asistidoService.generarVistaAsistida(originalText, definicion, response.getMensaje(), definicion.getCampos(), new ArrayList<>());
            }
        }

        limpiarColumnasTecnicas(response);
        return response;
    }
    
    /**
     * Genera vista previa para exportación con límites más altos.
     */
    public PreviewResponseDto generarPreviewExportacion(ReporteResponseDto definicion, String originalText, String usuarioId) {
        if (definicion != null && "cuellos_botella".equals(definicion.getIntencionDetectada())) {
            return generarReporteCuellosBotella(usuarioId, definicion, originalText);
        }

        // Build contract & repair plan first
        ReporteOutputContractService.OutputContract contract = outputContractService.buildContract(originalText, definicion);
        outputContractService.repairPlan(definicion, contract);

        try {
            // Resolver y reparar el plan con el resolutor universal
            universalResolverService.resolverPlan(definicion, originalText);
        } catch (IllegalArgumentException e) {
            log.warn("Error al resolver plan para exportacion: {}", e.getMessage());
            PreviewResponseDto errorResponse = new PreviewResponseDto();
            errorResponse.setInterpretacion(definicion);
            errorResponse.setError("CAMPO_NO_RESOLUBLE");
            errorResponse.setMensaje(e.getMessage());
            errorResponse.setFilas(new java.util.ArrayList<>());
            errorResponse.setColumnas(new java.util.ArrayList<>());
            errorResponse.setTotal(0);
            return errorResponse;
        }

        if (definicion.getRequiereAclaracion() != null && definicion.getRequiereAclaracion()) {
            PreviewResponseDto response = new PreviewResponseDto();
            response.setInterpretacion(definicion);
            response.setFilas(new java.util.ArrayList<>());
            response.setColumnas(new java.util.ArrayList<>());
            response.setTotal(0);
            response.setMensaje(definicion.getPreguntaAclaratoria());
            return response;
        }

        // Permitir más resultados para exportación, máximo 5000
        if (definicion.getLimite() == null || definicion.getLimite() > 5000) {
            definicion.setLimite(5000);
        }
        
        // Normalizar JSON de IA
        jsonNormalizer.normalizar(definicion, originalText);

        List<Map> resultados = null;
        try {
            resultados = aggregationBuilder.ejecutarConsulta(definicion);
        } catch (IllegalArgumentException e) {
            PreviewResponseDto errorResponse = new PreviewResponseDto();
            errorResponse.setInterpretacion(definicion);
            errorResponse.setError(e.getMessage().contains("Entidad no permitida") ? "ENTIDAD_NO_REPORTABLE" : "CAMPO_NO_REPORTABLE");
            errorResponse.setMensaje(e.getMessage());
            errorResponse.setFilas(new java.util.ArrayList<>());
            errorResponse.setColumnas(new java.util.ArrayList<>());
            errorResponse.setTotal(0);
            return errorResponse;
        }

        List<Map<String, Object>> filas = new java.util.ArrayList<>();
        if (resultados != null) {
            for (Map m : resultados) {
                filas.add((Map<String, Object>) m);
            }
        }

        // Validate output contract
        List<String> contractErrors = outputContractService.validate(filas, contract);
        if (!contractErrors.isEmpty()) {
            log.warn("El reporte de exportación falló la validación del contrato: {}. Reintentando reparación.", contractErrors);
            outputContractService.repairPlan(definicion, contract);
            try {
                universalResolverService.resolverPlan(definicion, originalText);
                jsonNormalizer.normalizar(definicion, originalText);
                resultados = aggregationBuilder.ejecutarConsulta(definicion);
                
                filas = new java.util.ArrayList<>();
                if (resultados != null) {
                    for (Map m : resultados) {
                        filas.add((Map<String, Object>) m);
                    }
                }
                contractErrors = outputContractService.validate(filas, contract);
            } catch (Exception ex) {
                log.error("Error al re-ejecutar exportación reparada: ", ex);
            }

            if (!contractErrors.isEmpty()) {
                PreviewResponseDto errorResponse = new PreviewResponseDto();
                errorResponse.setInterpretacion(definicion);
                errorResponse.setError("CONTRATO_DE_SALIDA_NO_CUMPLIDO");
                errorResponse.setMensaje("No se pudo cumplir el contrato de salida del reporte: " + contractErrors.get(0));
                errorResponse.setFilas(new java.util.ArrayList<>());
                errorResponse.setColumnas(new java.util.ArrayList<>());
                errorResponse.setTotal(0);
                return errorResponse;
            }
        }
        
        // Registrar exportación en auditoría
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(originalText)
                    .jsonInterpretado(objectMapper.writeValueAsString(definicion))
                    .entidadPrincipal(definicion.getEntidadPrincipal())
                    .intencionDetectada(definicion.getIntencionDetectada())
                    .formatoSalida(definicion.getFormatoSalida() != null ? definicion.getFormatoSalida() : "exportacion")
                    .visualizacion(definicion.getVisualizacion())
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXPORTADO")
                    .cantidadResultados(resultados.size())
                    .confianzaModelo(definicion.getConfianza())
                    .motorUsado(definicion.getMotor() != null ? definicion.getMotor() : "DESCONOCIDO")
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoría de exportación: ", e);
        }

        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        response.setFilas(filas);
        response.setTotal(filas.size());
        if (!filas.isEmpty()) {
            response.setColumnas(new java.util.ArrayList<>(filas.get(0).keySet()));
        } else {
            response.setColumnas(new java.util.ArrayList<>());
            response.setMensaje("No se puede exportar porque el reporte no tiene resultados.");
        }
        limpiarColumnasTecnicas(response);
        return response;
    }
    
    public List<ReporteGenerado> getHistorial() {
        return repository.findTop20ByOrderByFechaGeneracionDesc();
    }

    private void diagnosticarConsultaSinResultados(ReporteResponseDto definicion, PreviewResponseDto response) {
        String entidad = definicion.getEntidadPrincipal();
        com.leo.politicas_de_negocio.reportes.model.EntidadReportable entObj = catalogoService.obtenerEntidadPorNombreOAlias(entidad);
        String coleccionMongo = (entObj != null) ? entObj.getColeccionMongo() : entidad;

        boolean tieneDatos = false;
        try {
            tieneDatos = mongoTemplate.getCollection(coleccionMongo).countDocuments() > 0;
        } catch (Exception e) {
            log.warn("Error al contar documentos de " + coleccionMongo, e);
        }

        PreviewResponseDto.DiagnosticoConsulta diag = new PreviewResponseDto.DiagnosticoConsulta();
        diag.setColeccionTieneDatos(tieneDatos);
        diag.setCampoExiste(true);
        diag.setValoresDisponibles(new java.util.ArrayList<>());

        List<String> sugerencias = new java.util.ArrayList<>();

        if (!tieneDatos) {
            response.setMensaje("La colección '" + entidad + "' está vacía.");
            sugerencias.add("No hay datos registrados en '" + entidad + "'. Registra algunas operaciones primero.");
            response.setSugerencias(sugerencias);
            response.setDiagnostico(diag);
            return;
        }

        response.setMensaje("No se encontraron resultados para los criterios seleccionados.");

        if (definicion.getFiltros() != null && !definicion.getFiltros().isEmpty()) {
            for (FiltroDto filtro : definicion.getFiltros()) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(entidad, filtro.getCampo());
                if (rf != null) {
                    try {
                        String targetEntity = rf.getPath().isEmpty() ? entidad : rf.getPath().get(rf.getPath().size() - 1).getTarget();
                        List<Object> distinctValues = catalogoService.obtenerValoresDistintos(targetEntity, rf.getTargetFieldName());
                        
                        List<String> valuesStr = new ArrayList<>();
                        for (Object v : distinctValues) {
                            if (v != null) valuesStr.add(String.valueOf(v));
                        }
                        
                        if (!valuesStr.contains(String.valueOf(filtro.getValor()))) {
                            response.setMensaje("No se encontraron resultados con " + filtro.getCampo() + " = '" + filtro.getValor() + "'.");
                            diag.setValorSolicitado(String.valueOf(filtro.getValor()));
                            diag.setValoresDisponibles(valuesStr);
                            if (!valuesStr.isEmpty()) {
                                sugerencias.add("Valores reales en base de datos para '" + filtro.getCampo() + "': " + String.join(", ", valuesStr));
                            } else {
                                sugerencias.add("El campo '" + filtro.getCampo() + "' no contiene datos en los registros actuales.");
                            }
                            break;
                        }
                    } catch (Exception ex) {
                        log.warn("Error diagnosticando filtro: " + filtro.getCampo(), ex);
                    }
                }
            }
        }

        sugerencias.add("Intenta remover o ampliar los filtros aplicados.");
        sugerencias.add("Verifica si los tipos de datos en la relación coinciden (String u ObjectId).");
        response.setSugerencias(sugerencias);
        response.setDiagnostico(diag);
        response.setDetalleTecnico("La consulta a " + entidad + " retornó 0 documentos. Diagnóstico completado.");
    }

    public Map<String, Object> probarConsulta(ReporteResponseDto planOriginal) {
        Map<String, Object> debugResult = new LinkedHashMap<>();
        debugResult.put("planOriginal", planOriginal);

        ReporteResponseDto planReparado = new ReporteResponseDto();
        // Clona superficialmente el plan para no alterar la respuesta original
        planReparado.setTitulo(planOriginal.getTitulo());
        planReparado.setDescripcion(planOriginal.getDescripcion());
        planReparado.setIntencionDetectada(planOriginal.getIntencionDetectada());
        planReparado.setEntidadPrincipal(planOriginal.getEntidadPrincipal());
        planReparado.setCampos(planOriginal.getCampos() != null ? new ArrayList<>(planOriginal.getCampos()) : null);
        planReparado.setMetricas(planOriginal.getMetricas() != null ? new ArrayList<>(planOriginal.getMetricas()) : null);
        planReparado.setFiltros(planOriginal.getFiltros() != null ? new ArrayList<>(planOriginal.getFiltros()) : null);
        planReparado.setAgrupaciones(planOriginal.getAgrupaciones() != null ? new ArrayList<>(planOriginal.getAgrupaciones()) : null);
        planReparado.setOrdenamiento(planOriginal.getOrdenamiento() != null ? new ArrayList<>(planOriginal.getOrdenamiento()) : null);
        planReparado.setLimite(planOriginal.getLimite());
        planReparado.setFormatoSalida(planOriginal.getFormatoSalida());
        planReparado.setVisualizacion(planOriginal.getVisualizacion());

        debugResult.put("planReparado", planReparado);
        debugResult.put("entidadBase", planOriginal.getEntidadPrincipal());

        List<String> camposResueltos = new ArrayList<>();
        List<String> rutasUsadas = new ArrayList<>();
        List<String> lookupsGenerados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();

        try {
            universalResolverService.resolverPlan(planReparado, null);
            
            Set<String> referencedFields = new LinkedHashSet<>();
            if (planOriginal.getCampos() != null) referencedFields.addAll(planOriginal.getCampos());
            if (planOriginal.getAgrupaciones() != null) referencedFields.addAll(planOriginal.getAgrupaciones());
            if (planOriginal.getFiltros() != null) {
                for (FiltroDto f : planOriginal.getFiltros()) {
                    referencedFields.add(f.getCampo());
                }
            }

            for (String field : referencedFields) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(planOriginal.getEntidadPrincipal(), field);
                if (rf != null) {
                    camposResueltos.add(field + " -> " + rf.getResolvedMongoPath());
                    if (!rf.getPath().isEmpty()) {
                        StringBuilder ruta = new StringBuilder(planOriginal.getEntidadPrincipal());
                        for (int i = 0; i < rf.getPath().size(); i++) {
                            var step = rf.getPath().get(i);
                            ruta.append(" -> ").append(step.getTarget());
                            lookupsGenerados.add(String.format("from: %s, local: %s, foreign: %s, alias: %s",
                                    step.getFromCollection(), step.getLocalField(), step.getForeignField(),
                                    ReporteCampoResolver.construirAliasPath(rf.getPath().subList(0, i + 1))));
                        }
                        rutasUsadas.add(ruta.toString());
                    }
                } else {
                    advertencias.add("El campo '" + field + "' no pudo ser resuelto.");
                }
            }
        } catch (Exception e) {
            errores.add("Error al resolver plan: " + e.getMessage());
        }

        debugResult.put("camposResueltos", camposResueltos);
        debugResult.put("rutasUsadas", new ArrayList<>(new LinkedHashSet<>(rutasUsadas))); // Deduplicar
        debugResult.put("lookupsGenerados", new ArrayList<>(new LinkedHashSet<>(lookupsGenerados))); // Deduplicar

        if (errores.isEmpty()) {
            try {
                List<Map> mappedResults = aggregationBuilder.ejecutarConsulta(planReparado);
                debugResult.put("preview", mappedResults);
            } catch (Exception e) {
                errores.add("Error al ejecutar consulta del plan: " + e.getMessage());
                debugResult.put("preview", Collections.emptyList());
            }
        } else {
            debugResult.put("preview", Collections.emptyList());
        }

        debugResult.put("errores", errores);
        debugResult.put("advertencias", advertencias);

        return debugResult;
    }

    private void limpiarColumnasTecnicas(PreviewResponseDto response) {
        if (response == null || response.getFilas() == null || response.getFilas().isEmpty()) {
            return;
        }

        List<Map<String, Object>> filas = response.getFilas();
        List<Map<String, Object>> filasLimpias = new java.util.ArrayList<>();

        for (Map<String, Object> fila : filas) {
            Map<String, Object> filaLimpia = new LinkedHashMap<>();
            Set<String> keys = fila.keySet();
            
            for (Map.Entry<String, Object> entry : fila.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // 1. Excluir tokensJoin, datosContexto o campos temporales de lookup
                if ("tokensJoin".equalsIgnoreCase(key) || "datosContexto".equalsIgnoreCase(key) || key.contains("_lookup")) {
                    continue;
                }

                // 2. Excluir [object Object] o N/A
                if (value != null) {
                    String valStr = String.valueOf(value).trim();
                    if ("[object Object]".equalsIgnoreCase(valStr) || "N/A".equalsIgnoreCase(valStr)) {
                        continue;
                    }
                }

                // 3. Excluir IDs crudos si existe su versión enriquecida (Nombre, Nombres, Correo)
                if ("politicaId".equalsIgnoreCase(key) && keys.contains("politicaNombre")) {
                    continue;
                }
                if ("usuarioId".equalsIgnoreCase(key) && keys.contains("usuarioNombre")) {
                    continue;
                }
                if ("creadaPor".equalsIgnoreCase(key) && keys.contains("creadaPorNombre")) {
                    continue;
                }
                if ("responsableId".equalsIgnoreCase(key) && keys.contains("responsableNombre")) {
                    continue;
                }
                if ("funcionarioAsignado".equalsIgnoreCase(key) && keys.contains("funcionarioNombre")) {
                    continue;
                }
                if ("departamentoId".equalsIgnoreCase(key) && keys.contains("departamentoNombre")) {
                    continue;
                }
                if ("usuariosIniciadores".equalsIgnoreCase(key) && keys.contains("usuariosIniciadoresNombres")) {
                    continue;
                }
                if ("subidoPor".equalsIgnoreCase(key) && (keys.contains("usuarioNombre") || keys.contains("subidoPorNombre"))) {
                    continue;
                }

                if (key.endsWith("Id") || "creadaPor".equals(key) || "subidoPor".equals(key) || "funcionarioAsignado".equals(key)) {
                    String nameKey = key + "Nombre";
                    String namesKey = key + "Nombres";
                    String emailKey = key + "Correo";
                    if (keys.contains(nameKey) || keys.contains(namesKey) || keys.contains(emailKey)) {
                        continue;
                    }
                }

                filaLimpia.put(key, value);
            }
            filasLimpias.add(filaLimpia);
        }

        response.setFilas(filasLimpias);
        if (!filasLimpias.isEmpty()) {
            Set<String> uniqueKeys = new LinkedHashSet<>();
            for (Map<String, Object> fila : filasLimpias) {
                uniqueKeys.addAll(fila.keySet());
            }
            response.setColumnas(new java.util.ArrayList<>(uniqueKeys));
        } else {
            response.setColumnas(new java.util.ArrayList<>());
        }
    }

    private PreviewResponseDto generarReporteCuellosBotella(String usuarioId, ReporteResponseDto definicion, String originalText) {
        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        
        List<Map<String, Object>> filas = new ArrayList<>();
        List<String> columnas = Arrays.asList("tipo", "nombre", "severidad", "evidencia", "impacto", "recomendacion");
        response.setColumnas(columnas);
        
        try {
            com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse iaResponse = 
                analyticsService.getBottlenecks(usuarioId);
            
            if (iaResponse != null && iaResponse.isAvailable() && iaResponse.getBottlenecks() != null && !iaResponse.getBottlenecks().isEmpty()) {
                for (com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse.BottleneckItem item : iaResponse.getBottlenecks()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("tipo", item.getType());
                    fila.put("nombre", item.getName());
                    fila.put("severidad", item.getSeverity());
                    fila.put("evidencia", item.getEvidence());
                    fila.put("impacto", item.getImpact());
                    fila.put("recomendacion", item.getRecommendation());
                    filas.add(fila);
                }
                response.setFilas(filas);
                response.setTotal(filas.size());
                response.setMensaje(iaResponse.getSummary());
            } else {
                response.setFilas(filas);
                response.setTotal(0);
                response.setMensaje("No se detectaron cuellos de botella con la evidencia disponible.");
            }
        } catch (Exception e) {
            log.error("Error al obtener reporte de cuellos de botella: ", e);
            response.setFilas(filas);
            response.setTotal(0);
            response.setMensaje("No se detectaron cuellos de botella con la evidencia disponible.");
        }
        
        // Registrar en historial de auditoría
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(originalText)
                    .jsonInterpretado(objectMapper.writeValueAsString(definicion))
                    .entidadPrincipal(definicion.getEntidadPrincipal())
                    .intencionDetectada(definicion.getIntencionDetectada())
                    .formatoSalida("pantalla")
                    .visualizacion(definicion.getVisualizacion())
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXITO")
                    .cantidadResultados(filas.size())
                    .confianzaModelo(definicion.getConfianza())
                    .motorUsado(definicion.getMotor() != null ? definicion.getMotor() : "DESCONOCIDO")
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoría de reporte de cuellos de botella: ", e);
        }
        
        return response;
    }
}

