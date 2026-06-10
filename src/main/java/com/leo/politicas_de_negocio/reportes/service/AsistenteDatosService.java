package com.leo.politicas_de_negocio.reportes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.politicas_de_negocio.reportes.dto.*;
import com.leo.politicas_de_negocio.reportes.model.ReporteGenerado;
import com.leo.politicas_de_negocio.reportes.repository.ReporteGeneradoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio del Asistente de Datos Inteligente.
 * Orquesta el flujo: IA interpreta → backend valida → MongoDB responde → IA genera respuesta.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsistenteDatosService {

    private final MongoTemplate mongoTemplate;
    private final ReporteCatalogoService catalogoService;
    private final ReporteJsonNormalizer jsonNormalizer;
    private final ReporteGeneradoRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.ia.deep-learning-url:http://localhost:8010}")
    private String iaServiceUrl;

    /**
     * Procesa una pregunta libre del usuario.
     * Flujo completo: IA interpreta → validación → consulta MongoDB → IA genera respuesta.
     */
    public AsistenteDatosResponseDto preguntar(AsistenteDatosRequestDto request, String usuarioId, String rol) {
        AsistenteDatosResponseDto response = new AsistenteDatosResponseDto();
        
        try {
            // 1. Enviar al Motor IA para generar plan de consulta
            Map<String, Object> planResult = obtenerPlanDesdeIA(request);
            
            if (planResult == null) {
                response.setRespuesta("El servicio de interpretación IA no está disponible en este momento.");
                response.setMotor("MOTOR_FALLBACK");
                response.setConfianza(0.0);
                response.setAdvertencias(List.of("Motor IA no disponible. Verifique que ia-deep-learning-service esté ejecutándose."));
                return response;
            }
            
            String motor = (String) planResult.getOrDefault("motor", "MOTOR_FALLBACK");
            Map<String, Object> plan = (Map<String, Object>) planResult.get("plan");
            
            if (plan == null) {
                response.setRespuesta("No se pudo generar un plan de consulta.");
                response.setMotor(motor);
                return response;
            }
            
            // Verificar si requiere aclaración
            Boolean requiereAclaracion = (Boolean) plan.getOrDefault("requiereAclaracion", false);
            if (Boolean.TRUE.equals(requiereAclaracion)) {
                response.setRespuesta((String) plan.getOrDefault("preguntaAclaratoria", "¿Podrías ser más específico?"));
                response.setPlan(plan);
                response.setMotor(motor);
                return response;
            }
            
            // 2. Normalizar plan
            jsonNormalizer.normalizarPlan(plan);
            
            // 3. Validar plan contra catálogo
            String entidadPrincipal = (String) plan.getOrDefault("entidadPrincipal", "");
            if (!entidadPrincipal.isEmpty() && !catalogoService.esEntidadPermitida(entidadPrincipal)) {
                response.setRespuesta("La entidad solicitada no está disponible para consulta: " + entidadPrincipal);
                response.setAdvertencias(List.of("Entidad no permitida: " + entidadPrincipal));
                response.setPlan(plan);
                response.setMotor(motor);
                return response;
            }
            
            // 3. Ejecutar consulta MongoDB
            List<Map<String, Object>> datos = ejecutarPlanConsulta(plan);
            
            // 4. Generar respuesta
            response.setDatos(datos);
            response.setPlan(plan);
            response.setMotor(motor);
            response.setConfianza(0.8);
            response.setFuentesConsultadas(List.of("mongo." + entidadPrincipal));
            
            if (!datos.isEmpty()) {
                // Extraer columnas de los datos
                response.setColumnas(new ArrayList<>(datos.get(0).keySet()));
                response.setVisualizacionSugerida("tabla");
                response.setRespuesta("Se encontraron " + datos.size() + " resultados para tu consulta.");
                response.setResumen(datos.size() + " registros recuperados.");
                response.setAccionesSugeridas(List.of("Exportar a Excel", "Exportar a PDF"));
            } else {
                response.setRespuesta("No se encontraron resultados para los criterios indicados.");
                response.setResumen("Sin resultados.");
            }
            
            // 5. Registrar en auditoría
            registrarAuditoria(request.getTexto(), plan, response, usuarioId, motor);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error en asistente de datos: ", e);
            response.setRespuesta("Ocurrió un error al procesar tu consulta. Por favor intenta de nuevo.");
            response.setMotor("MOTOR_FALLBACK");
            response.setAdvertencias(List.of("Error interno: " + e.getMessage()));
            return response;
        }
    }

    /**
     * Solo genera el plan sin ejecutar la consulta.
     */
    public Map<String, Object> planificar(AsistenteDatosRequestDto request, String usuarioId, String rol) {
        Map<String, Object> planResult = obtenerPlanDesdeIA(request);
        if (planResult == null) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("motor", "MOTOR_FALLBACK");
            fallback.put("plan", Map.of(
                    "requiereAclaracion", true,
                    "preguntaAclaratoria", "Motor IA no disponible."
            ));
            return fallback;
        }
        jsonNormalizer.normalizarPlan(planResult);
        return planResult;
    }

    /**
     * Ejecuta un plan ya generado y validado.
     */
    public AsistenteDatosResponseDto ejecutarPlan(Map<String, Object> plan, String textoOriginal, String usuarioId) {
        AsistenteDatosResponseDto response = new AsistenteDatosResponseDto();
        
        try {
            jsonNormalizer.normalizarPlan(plan);
            
            String entidadPrincipal = (String) plan.getOrDefault("entidadPrincipal", "");
            if (!entidadPrincipal.isEmpty() && !catalogoService.esEntidadPermitida(entidadPrincipal)) {
                response.setRespuesta("Entidad no permitida: " + entidadPrincipal);
                response.setMotor("MOTOR_FALLBACK");
                return response;
            }
            
            List<Map<String, Object>> datos = ejecutarPlanConsulta(plan);
            response.setDatos(datos);
            response.setPlan(plan);
            response.setFuentesConsultadas(List.of("mongo." + entidadPrincipal));
            
            if (!datos.isEmpty()) {
                response.setColumnas(new ArrayList<>(datos.get(0).keySet()));
                response.setRespuesta("Se encontraron " + datos.size() + " resultados.");
                response.setVisualizacionSugerida("tabla");
                response.setAccionesSugeridas(List.of("Exportar a Excel", "Exportar a PDF"));
            } else {
                response.setRespuesta("No se encontraron resultados.");
            }
            
            return response;
        } catch (Exception e) {
            log.error("Error ejecutando plan: ", e);
            response.setRespuesta("Error ejecutando el plan de consulta.");
            response.setAdvertencias(List.of(e.getMessage()));
            return response;
        }
    }

    // ========== Métodos internos ==========

    private Map<String, Object> obtenerPlanDesdeIA(AsistenteDatosRequestDto request) {
        try {
            String url = iaServiceUrl + "/api/ia/asistente-datos/preguntar";
            
            Map<String, String> body = new HashMap<>();
            body.put("texto", request.getTexto());
            body.put("rol", "ADMIN");
            if (request.getContextoAdicional() != null) {
                body.put("contextoAdicional", request.getContextoAdicional());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = restTemplate.postForObject(url, entity, Map.class);
            return result;
        } catch (Exception e) {
            log.error("Error comunicándose con Motor IA para plan de consulta: ", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> ejecutarPlanConsulta(Map<String, Object> plan) {
        String entidad = (String) plan.getOrDefault("entidadPrincipal", "");
        if (entidad.isEmpty() || !catalogoService.esEntidadPermitida(entidad)) {
            return Collections.emptyList();
        }

        List<AggregationOperation> operations = new ArrayList<>();

        // Filtros
        List<Map<String, Object>> filtros = (List<Map<String, Object>>) plan.getOrDefault("filtros", Collections.emptyList());
        for (Map<String, Object> filtro : filtros) {
            String campo = (String) filtro.getOrDefault("campo", "");
            String operador = (String) filtro.getOrDefault("operador", "");
            Object valor = filtro.get("valor");
            
            if (!catalogoService.esCampoPermitido(entidad, campo)) continue;
            
            Criteria c = Criteria.where(campo);
            switch (operador.toLowerCase()) {
                case "=": c.is(valor); break;
                case "!=": c.ne(valor); break;
                case ">": c.gt(valor); break;
                case ">=": c.gte(valor); break;
                case "<": c.lt(valor); break;
                case "<=": c.lte(valor); break;
                case "mes_actual":
                    String valStr = valor != null ? valor.toString().toLowerCase() : "";
                    int monthNum = parseMonthName(valStr);
                    if (monthNum > 0) {
                        int year = LocalDate.now().getYear();
                        LocalDate start = LocalDate.of(year, monthNum, 1);
                        LocalDate end = start.plusMonths(1).minusDays(1);
                        c.gte(start.atStartOfDay()).lte(end.atTime(23, 59, 59, 999000000));
                    } else {
                        c.gte(LocalDate.now().withDayOfMonth(1).atStartOfDay());
                    }
                    break;
                case "anio_actual":
                    c.gte(LocalDate.now().withDayOfYear(1).atStartOfDay()); break;
                case "ultimos_dias":
                    int dias = valor != null ? Integer.parseInt(valor.toString()) : 7;
                    c.gte(LocalDateTime.now().minusDays(dias)); break;
                case "ultimos_meses":
                    int meses = valor != null ? Integer.parseInt(valor.toString()) : 3;
                    c.gte(LocalDateTime.now().minusMonths(meses)); break;
                default: continue;
            }
            operations.add(Aggregation.match(c));
        }

        // Agrupaciones
        List<String> agrupaciones = (List<String>) plan.getOrDefault("agrupaciones", Collections.emptyList());
        if (!agrupaciones.isEmpty()) {
            for (String groupField : agrupaciones) {
                if (!catalogoService.esCampoPermitido(entidad, groupField)) {
                    throw new IllegalArgumentException("Campo de agrupación no permitido: " + groupField);
                }
            }
            String[] groupFields = agrupaciones.toArray(new String[0]);
            var groupOp = Aggregation.group(groupFields).count().as("total");
            operations.add(groupOp);
            
            if (agrupaciones.size() == 1) {
                String groupField = agrupaciones.get(0);
                if (groupField.equals("creadaPor") || groupField.equals("responsableId") || groupField.equals("usuarioId") || groupField.equals("usuarioSubio")) {
                    operations.add(Aggregation.addFields().addField("_idObj").withValueOfExpression("{$toObjectId: '$_id'}").build());
                    operations.add(Aggregation.lookup("usuarios", "_idObj", "_id", "usuarioDetalle"));
                    operations.add(Aggregation.unwind("usuarioDetalle", true));
                    
                    operations.add(Aggregation.project()
                            .and(org.springframework.data.mongodb.core.aggregation.ConditionalOperators.ifNull("usuarioDetalle.nombre").thenValueOf("_id")).as(groupField)
                            .andInclude("total")
                            .andExclude("_id"));
                } else if (groupField.equals("politicaId")) {
                    operations.add(Aggregation.addFields().addField("_idObj").withValueOfExpression("{$toObjectId: '$_id'}").build());
                    operations.add(Aggregation.lookup("politicas_negocio", "_idObj", "_id", "politicaDetalle"));
                    operations.add(Aggregation.unwind("politicaDetalle", true));
                    
                    operations.add(Aggregation.project()
                            .and(org.springframework.data.mongodb.core.aggregation.ConditionalOperators.ifNull("politicaDetalle.nombre").thenValueOf("_id")).as(groupField)
                            .andInclude("total")
                            .andExclude("_id"));
                } else {
                    operations.add(Aggregation.project()
                            .andExpression("_id").as(groupField)
                            .andInclude("total")
                            .andExclude("_id"));
                }
            }
        } else {
            // Sin agrupaciones: Listado simple
            List<String> campos = (List<String>) plan.getOrDefault("campos", Collections.emptyList());
            if (!campos.isEmpty()) {
                for (String campo : campos) {
                    if (!catalogoService.esCampoPermitido(entidad, campo)) {
                        throw new IllegalArgumentException("Campo solicitado no permitido: " + campo);
                    }
                }
                String[] fields = campos.toArray(new String[0]);
                operations.add(Aggregation.project(fields).andExclude("_id"));
            } else {
                operations.add(Aggregation.project().andExclude("_id"));
            }
        }

        // Ordenamiento
        List<Map<String, String>> ordenamientos = (List<Map<String, String>>) plan.getOrDefault("ordenamiento", Collections.emptyList());
        if (!ordenamientos.isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (Map<String, String> ord : ordenamientos) {
                String campOrd = ord.getOrDefault("campo", "");
                String dir = ord.getOrDefault("direccion", "desc");
                orders.add(new Sort.Order(
                        dir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                        campOrd
                 ));
            }
            operations.add(Aggregation.sort(Sort.by(orders)));
        }

        // Límite (máximo 500 por seguridad)
        int limite = plan.containsKey("limite") ? Math.min(((Number) plan.get("limite")).intValue(), 500) : 50;
        operations.add(Aggregation.limit(limite));

        if (operations.isEmpty()) {
            operations.add(Aggregation.limit(limite));
        }

        Aggregation agg = Aggregation.newAggregation(operations);
        List<Map> rawResults = mongoTemplate.aggregate(agg, entidad, Map.class).getMappedResults();
        List<Map<String, Object>> results = new ArrayList<>();
        if (rawResults != null) {
            for (Map raw : rawResults) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedMap = (Map<String, Object>) raw;
                results.add(typedMap);
            }
        }
        return results;
    }

    private void registrarAuditoria(String textoOriginal, Map<String, Object> plan,
                                     AsistenteDatosResponseDto response, String usuarioId, String motor) {
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(textoOriginal)
                    .planConsulta(objectMapper.writeValueAsString(plan))
                    .respuestaFinal(response.getRespuesta())
                    .entidadPrincipal((String) plan.getOrDefault("entidadPrincipal", ""))
                    .tipoConsulta((String) plan.getOrDefault("tipoConsulta", ""))
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXITO")
                    .cantidadResultados(response.getDatos() != null ? response.getDatos().size() : 0)
                    .confianzaModelo(response.getConfianza())
                    .motorUsado(motor)
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error registrando auditoría asistente datos: ", e);
        }
    }

    private int parseMonthName(String monthName) {
        if (monthName == null) return -1;
        switch (monthName.toLowerCase().trim()) {
            case "enero": return 1;
            case "febrero": return 2;
            case "marzo": return 3;
            case "abril": return 4;
            case "mayo": return 5;
            case "junio": return 6;
            case "julio": return 7;
            case "agosto": return 8;
            case "septiembre": return 9;
            case "octubre": return 10;
            case "noviembre": return 11;
            case "diciembre": return 12;
            default: return -1;
        }
    }
}
