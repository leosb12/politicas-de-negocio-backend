package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.analiticas.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteCompuestoService {

    private final MongoTemplate mongoTemplate;
    private final AnalyticsService analyticsService;

    public Map<String, Object> generarResumenEjecutivo() {
        return generarResumenEjecutivoConUsuario(null);
    }

    public Map<String, Object> generarResumenEjecutivoConUsuario(String usuarioId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tipoReporte", "resumen_ejecutivo");

        // Determinar rango de fechas. Si no hay datos del mes actual, retrocedemos al histórico.
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long tramitesIniciados = mongoTemplate.count(
                new Query(Criteria.where("fechaCreacion").gte(startOfMonth)),
                "instancias_politica");

        String periodoMsg = "del mes actual";
        if (tramitesIniciados == 0) {
            startOfMonth = LocalDateTime.of(2000, 1, 1, 0, 0);
            tramitesIniciados = mongoTemplate.count(
                    new Query(Criteria.where("fechaCreacion").gte(startOfMonth)),
                    "instancias_politica");
            periodoMsg = "histórico general (sin datos en el mes actual)";
        }

        // 1. Métricas para Tarjetas
        long tramitesFinalizados = mongoTemplate.count(new Query(
                Criteria.where("fechaCreacion").gte(startOfMonth)
                        .and("estadoInstancia").is("FINALIZADA")),
                "instancias_politica");

        long tareasPendientes = mongoTemplate.count(
                new Query(Criteria.where("estado").is("PENDIENTE")),
                "tareas_actividad");

        long documentosSubidos = mongoTemplate.count(new Query(), "archivos_adjuntos");
        long pagosPendientes = mongoTemplate.count(
                new Query(Criteria.where("estado").is("PENDIENTE")), "pagos");

        // Dinero generado: suma real de montos de pagos no pendientes
        double dineroGenerado = calcularDineroGenerado();

        // Cuellos de botella: usar endpoint IA real
        List<Map<String, Object>> cuellosList = obtenerCuellosBotella(usuarioId);
        long cuellosBotella = cuellosList.size();

        // Tarjetas
        List<Map<String, Object>> tarjetas = new ArrayList<>();
        tarjetas.add(crearTarjeta("Trámites iniciados", tramitesIniciados, "fa-folder-open"));
        tarjetas.add(crearTarjeta("Trámites finalizados", tramitesFinalizados, "fa-check-circle"));
        tarjetas.add(crearTarjeta("Tareas pendientes", tareasPendientes, "fa-tasks"));
        tarjetas.add(crearTarjeta("Cuellos de botella", cuellosBotella, "fa-hourglass-half"));
        tarjetas.add(crearTarjeta("Documentos subidos", documentosSubidos, "fa-file"));
        tarjetas.add(crearTarjeta("Pagos pendientes", pagosPendientes, "fa-money-bill"));

        // Tarjeta de dinero generado (solo mostrar si hay dato real)
        if (dineroGenerado > 0) {
            Map<String, Object> tarjetaDinero = new LinkedHashMap<>();
            tarjetaDinero.put("titulo", "Dinero generado");
            tarjetaDinero.put("valor", dineroGenerado);
            tarjetaDinero.put("icono", "fa-dollar-sign");
            tarjetaDinero.put("formato", "moneda");
            tarjetas.add(tarjetaDinero);
        }

        response.put("tarjetas", tarjetas);

        // 2. Tabla de Políticas Más Usadas — lookup robusto que tolera String y ObjectId
        List<Map<String, Object>> filasPoliticas = obtenerPoliticasMasUsadas(startOfMonth);

        List<Map<String, Object>> tablas = new ArrayList<>();
        Map<String, Object> tablaPoliticas = new LinkedHashMap<>();
        tablaPoliticas.put("titulo", "Políticas más usadas");
        tablaPoliticas.put("columnas", Arrays.asList("politicaNombre", "cantidadTramites"));
        tablaPoliticas.put("filas", filasPoliticas);
        tablas.add(tablaPoliticas);

        // 3. Tabla de Cuellos de Botella (si existen)
        if (!cuellosList.isEmpty()) {
            Map<String, Object> tablaCuellos = new LinkedHashMap<>();
            tablaCuellos.put("titulo", "Cuellos de botella detectados por IA");
            tablaCuellos.put("columnas", Arrays.asList("tipo", "nombre", "severidad", "evidencia", "recomendacion"));
            tablaCuellos.put("filas", cuellosList);
            tablas.add(tablaCuellos);
        }

        response.put("tablas", tablas);
        response.put("mensaje", "Resumen ejecutivo generado con datos " + periodoMsg + ".");

        return response;
    }

    /**
     * Calcula el dinero generado sumando montos de pagos (no pendientes si es posible).
     */
    private double calcularDineroGenerado() {
        try {
            // Intentar primero con pagos no pendientes (realizados/confirmados)
            String aggStr = "{ $group: { _id: null, total: { $sum: '$monto' } } }";
            AggregationOperation matchPagados = context ->
                    Document.parse("{ $match: { estado: { $ne: 'PENDIENTE' } } }");
            AggregationOperation group = context -> Document.parse(aggStr);
            Aggregation agg = Aggregation.newAggregation(matchPagados, group);
            List<Map> result = mongoTemplate.aggregate(agg, "pagos", Map.class).getMappedResults();
            if (!result.isEmpty() && result.get(0).get("total") != null) {
                Object total = result.get(0).get("total");
                return ((Number) total).doubleValue();
            }
            // Si no hay pagos no-pendientes, sumar todos
            AggregationOperation groupAll = context -> Document.parse(aggStr);
            Aggregation aggAll = Aggregation.newAggregation(groupAll);
            List<Map> resultAll = mongoTemplate.aggregate(aggAll, "pagos", Map.class).getMappedResults();
            if (!resultAll.isEmpty() && resultAll.get(0).get("total") != null) {
                return ((Number) resultAll.get(0).get("total")).doubleValue();
            }
        } catch (Exception e) {
            log.warn("Error calculando dinero generado: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Obtiene la lista de políticas más usadas con lookup robusto que tolera
     * mezcla de tipos String y ObjectId entre instancias_politica.politicaId
     * y politicas_negocio._id.
     */
    private List<Map<String, Object>> obtenerPoliticasMasUsadas(LocalDateTime startOfMonth) {
        try {
            // Pipeline manual con $lookup tolerante a tipos (usa $expr con $convert)
            String lookupJson = "{ $lookup: { " +
                    "  from: 'politicas_negocio', " +
                    "  let: { pid: '$_id' }, " +
                    "  pipeline: [ " +
                    "    { $match: { $expr: { $or: [ " +
                    "      { $eq: ['$_id', '$$pid'] }, " +
                    "      { $eq: ['$_id', { $convert: { input: '$$pid', to: 'objectId', onError: null, onNull: null } }] }, " +
                    "      { $eq: [{ $toString: '$_id' }, '$$pid'] } " +
                    "    ] } } } " +
                    "  ], " +
                    "  as: 'politicaDetalle' " +
                    "} }";

            AggregationOperation matchFecha = Aggregation.match(
                    Criteria.where("fechaCreacion").gte(startOfMonth));
            AggregationOperation group = context ->
                    Document.parse("{ $group: { _id: '$politicaId', cantidadTramites: { $sum: 1 } } }");
            AggregationOperation lookup = context -> Document.parse(lookupJson);
            AggregationOperation unwind = Aggregation.unwind("politicaDetalle", true);

            // Proyección que usa nombre real si existe, caso contrario diagnóstica
            AggregationOperation project = context -> Document.parse(
                    "{ $project: { " +
                    "  politicaNombre: { $cond: [ " +
                    "    { $and: [ " +
                    "      { $gt: ['$politicaDetalle', null] }, " +
                    "      { $ifNull: ['$politicaDetalle.nombre', false] } " +
                    "    ] }, " +
                    "    '$politicaDetalle.nombre', " +
                    "    { $cond: [ { $gt: ['$_id', null] }, { $concat: ['Política ID: ', { $ifNull: ['$_id', 'desconocido'] }] }, 'Política no encontrada'] } " +
                    "  ] }, " +
                    "  cantidadTramites: 1, _id: 0 " +
                    "} }");

            AggregationOperation sort = Aggregation.sort(Sort.Direction.DESC, "cantidadTramites");
            AggregationOperation limit = Aggregation.limit(5);

            Aggregation agg = Aggregation.newAggregation(matchFecha, group, lookup, unwind, project, sort, limit);
            List<Map> raw = mongoTemplate.aggregate(agg, "instancias_politica", Map.class).getMappedResults();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map m : raw) {
                result.add((Map<String, Object>) m);
            }
            return result;
        } catch (Exception e) {
            log.error("Error al obtener políticas más usadas: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Obtiene los cuellos de botella del endpoint IA real.
     */
    private List<Map<String, Object>> obtenerCuellosBotella(String usuarioId) {
        try {
            com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse iaResponse =
                    analyticsService.getBottlenecks(usuarioId != null ? usuarioId : "system");
            if (iaResponse != null && iaResponse.isAvailable()
                    && iaResponse.getBottlenecks() != null && !iaResponse.getBottlenecks().isEmpty()) {
                List<Map<String, Object>> filas = new ArrayList<>();
                for (com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse.BottleneckItem item
                        : iaResponse.getBottlenecks()) {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("tipo", item.getType());
                    fila.put("nombre", item.getName());
                    fila.put("severidad", item.getSeverity());
                    fila.put("evidencia", item.getEvidence());
                    fila.put("impacto", item.getImpact());
                    fila.put("recomendacion", item.getRecommendation());
                    filas.add(fila);
                }
                return filas;
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener cuellos de botella del servicio IA: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private Map<String, Object> crearTarjeta(String titulo, long valor, String icono) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("titulo", titulo);
        t.put("valor", valor);
        t.put("icono", icono);
        return t;
    }
}
