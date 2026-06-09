package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;
import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.OrdenamientoDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteVisualQueryService {

    private final MongoTemplate mongoTemplate;
    private final ReporteMongoAggregationBuilder aggregationBuilder;
    private final ReporteCatalogoService catalogoService;
    private final ReporteAsistidoService asistidoService;

    public List<Map> ejecutarConsultaMetrica(String metrica, String entidadPrincipal, Map<String, Object> filtros, int limite) {
        return ejecutarConsultaMetrica(metrica, entidadPrincipal, filtros, limite, false);
    }

    @SuppressWarnings("unchecked")
    public List<Map> ejecutarConsultaMetrica(String metrica, String entidadPrincipal, Map<String, Object> filtros, int limite, Boolean iaPlus) {
        log.info("Ejecutando consulta visual para metrica: '{}', entidad: '{}', filtros: {}, iaPlus: {}", metrica, entidadPrincipal, filtros, iaPlus);

        // Validar entidad
        if (entidadPrincipal != null && !catalogoService.esEntidadPermitida(entidadPrincipal)) {
            throw new IllegalArgumentException("La entidad solicitada no está permitida en el catálogo: " + entidadPrincipal);
        }

        if (Boolean.TRUE.equals(iaPlus)) {
            ReporteResponseDto definicion = construirDefinicionReporte(metrica, entidadPrincipal, filtros, limite);
            if (definicion != null) {
                List<String> columnasEsperadas = new ArrayList<>();
                if (definicion.getAgrupaciones() != null) {
                    columnasEsperadas.addAll(definicion.getAgrupaciones());
                }
                if (definicion.getMetricas() != null) {
                    for (MetricaDto m : definicion.getMetricas()) {
                        columnasEsperadas.add(m.getAlias());
                    }
                }
                if (columnasEsperadas.isEmpty() && definicion.getCampos() != null) {
                    columnasEsperadas.addAll(definicion.getCampos());
                }
                PreviewResponseDto preview = asistidoService.generarVistaAsistida(
                        metrica + " para " + entidadPrincipal,
                        definicion,
                        "Modo Asistencia IA+ activa",
                        columnasEsperadas,
                        new ArrayList<>()
                );
                if (preview != null && preview.getFilas() != null) {
                    return (List<Map>) (List) preview.getFilas();
                }
            }
        }

        // Casos especiales de agregación compleja
        if ("tramites_por_mes".equalsIgnoreCase(metrica)) {
            return ejecutarTramitesPorMes(filtros, limite);
        } else if ("promedio_tiempo_finalizacion".equalsIgnoreCase(metrica)) {
            return ejecutarPromedioTiempoFinalizacion(filtros, limite);
        }

        // Mapeo estándar a ReporteResponseDto para reutilizar ReporteMongoAggregationBuilder
        ReporteResponseDto definicion = construirDefinicionReporte(metrica, entidadPrincipal, filtros, limite);
        if (definicion == null) {
            throw new IllegalArgumentException("La métrica solicitada no está soportada o no tiene mapeo configurado: " + metrica);
        }

        return aggregationBuilder.ejecutarConsulta(definicion);
    }

    private ReporteResponseDto construirDefinicionReporte(String metrica, String entidadPrincipal, Map<String, Object> filtros, int limite) {
        ReporteResponseDto def = new ReporteResponseDto();
        def.setLimite(limite > 0 ? limite : 10);
        def.setRequiereResolucionBackend(true);
        def.setRequiereAclaracion(false);

        List<FiltroDto> filtrosList = parsearFiltros(filtros);
        def.setFiltros(filtrosList);

        switch (metrica.toLowerCase()) {
            case "tramites_por_mes":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("mes"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "promedio_tiempo_finalizacion":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(new ArrayList<>());
                def.setMetricas(Collections.singletonList(new MetricaDto("avg", "duracionDias", "promedio")));
                break;

            case "funcionarios_mas_activos":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("funcionarioNombre"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                def.setOrdenamiento(Collections.singletonList(new OrdenamientoDto("cantidad", "desc")));
                break;

            case "clientes_mas_inician_politicas":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("creadaPorNombre"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                def.setOrdenamiento(Collections.singletonList(new OrdenamientoDto("cantidad", "desc")));
                break;

            case "administradores_mas_politicas_crearon":
                def.setEntidadPrincipal("politicas_negocio");
                def.setAgrupaciones(Collections.singletonList("creadaPorNombre"));
                // Filtrar usuarios administradores en la consulta si aplica, por defecto agrupar por creador
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                def.setOrdenamiento(Collections.singletonList(new OrdenamientoDto("cantidad", "desc")));
                break;

            case "politicas_mas_usadas":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("politicaNombre"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                def.setOrdenamiento(Collections.singletonList(new OrdenamientoDto("cantidad", "desc")));
                break;

            case "politicas_por_estado":
                def.setEntidadPrincipal("politicas_negocio");
                def.setAgrupaciones(Collections.singletonList("estado"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "tramites_por_estado":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("estadoInstancia"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "tramites_por_departamento":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("departamentoNombre"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "tramites_por_prioridad":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("prioridad"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "tramites_finalizados_por_funcionario":
                def.setEntidadPrincipal("instancias_politica");
                def.setAgrupaciones(Collections.singletonList("funcionarioNombre"));
                FiltroDto fFinalizada = new FiltroDto();
                fFinalizada.setCampo("estadoInstancia");
                fFinalizada.setOperador("=");
                fFinalizada.setValor("FINALIZADA");
                filtrosList.add(fFinalizada);
                def.setFiltros(filtrosList);
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "pagos_por_estado":
                def.setEntidadPrincipal("pagos");
                def.setAgrupaciones(Collections.singletonList("estado"));
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "cantidad")));
                break;

            case "pagos_por_politica":
                def.setEntidadPrincipal("pagos");
                def.setAgrupaciones(Collections.singletonList("politicaNombre"));
                def.setMetricas(Collections.singletonList(new MetricaDto("sum", "monto", "totalRecaudado")));
                def.setOrdenamiento(Collections.singletonList(new OrdenamientoDto("totalRecaudado", "desc")));
                break;

            case "total_tramites":
                def.setEntidadPrincipal("instancias_politica");
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "total")));
                break;

            case "total_politicas":
                def.setEntidadPrincipal("politicas_negocio");
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "total")));
                break;

            case "total_usuarios":
                def.setEntidadPrincipal("usuarios");
                def.setMetricas(Collections.singletonList(new MetricaDto("count", "id", "total")));
                break;

            case "total_pagos":
                def.setEntidadPrincipal("pagos");
                def.setMetricas(Collections.singletonList(new MetricaDto("sum", "monto", "totalRecaudado")));
                break;

            default:
                return null;
        }

        return def;
    }

    private List<FiltroDto> parsearFiltros(Map<String, Object> filtrosMap) {
        List<FiltroDto> list = new ArrayList<>();
        if (filtrosMap != null) {
            for (Map.Entry<String, Object> entry : filtrosMap.entrySet()) {
                FiltroDto f = new FiltroDto();
                f.setCampo(entry.getKey());
                f.setOperador("=");
                f.setValor(entry.getValue());
                list.add(f);
            }
        }
        return list;
    }

    // --- AGREGACIONES MANUALES PERSONALIZADAS ---

    private List<Map> ejecutarTramitesPorMes(Map<String, Object> filtros, int limite) {
        AggregationOperation project = context -> Document.parse("{ $project: { mes: { $dateToString: { format: '%Y-%m', date: '$fechaCreacion' } } } }");
        AggregationOperation group = context -> Document.parse("{ $group: { _id: '$mes', cantidad: { $sum: 1 } } }");
        AggregationOperation sort = context -> Document.parse("{ $sort: { _id: 1 } }");
        AggregationOperation limitOp = Aggregation.limit(limite > 0 ? limite : 12);

        Aggregation agg = Aggregation.newAggregation(project, group, sort, limitOp);
        List<Map> results = mongoTemplate.aggregate(agg, "instancias_politica", Map.class).getMappedResults();
        
        // Formatear la clave de agrupación a "fecha" o "mes" para uniformidad
        List<Map> formatted = new ArrayList<>();
        for (Map r : results) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("mes", r.get("_id"));
            row.put("cantidad", r.get("cantidad"));
            formatted.add(row);
        }
        return formatted;
    }

    private List<Map> ejecutarPromedioTiempoFinalizacion(Map<String, Object> filtros, int limite) {
        AggregationOperation match = context -> Document.parse("{ $match: { estadoInstancia: 'FINALIZADA', fechaFinalizacion: { $ne: null }, fechaCreacion: { $ne: null } } }");
        // Calcular en días
        AggregationOperation project = context -> Document.parse("{ $project: { duracionDias: { $divide: [ { $subtract: [ '$fechaFinalizacion', '$fechaCreacion' ] }, 86400000 ] } } }");
        AggregationOperation group = context -> Document.parse("{ $group: { _id: null, promedio: { $avg: '$duracionDias' } } }");

        Aggregation agg = Aggregation.newAggregation(match, project, group);
        List<Map> results = mongoTemplate.aggregate(agg, "instancias_politica", Map.class).getMappedResults();
        
        List<Map> formatted = new ArrayList<>();
        if (!results.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            // Formatear a 2 decimales para la visualización del KPI
            double promedio = ((Number) results.get(0).get("promedio")).doubleValue();
            row.put("promedio", Math.round(promedio * 100.0) / 100.0);
            formatted.add(row);
        } else {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("promedio", 0.0);
            formatted.add(row);
        }
        return formatted;
    }
}
