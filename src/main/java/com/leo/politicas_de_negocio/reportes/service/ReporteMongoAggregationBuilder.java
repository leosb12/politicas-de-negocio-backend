package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;
import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.OrdenamientoDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteMongoAggregationBuilder {

    private final MongoTemplate mongoTemplate;
    private final ReporteCatalogoService catalogoService;
    private final ReporteCampoResolver campoResolver;

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class LookupInfo {
        private ReporteRelacionGraphService.RelationshipStep step;
        private String localFieldWithPrefix;
        private String aliasPath;
    }

    /**
     * Crea una operación de lookup que tolera mezclas de tipos String y ObjectId.
     */
    public static AggregationOperation crearLookupSeguro(String localField, String foreignCollection,
            String foreignField, String alias) {
        String lookupJson = String.format(
                "{ $lookup: { " +
                        "  from: '%s', " +
                        "  let: { localVal: '$%s' }, " +
                        "  pipeline: [ " +
                        "    { $match: { " +
                        "        $expr: { " +
                        "          $or: [ " +
                        "            { $eq: [ '$%s', '$$localVal' ] }, " +
                        "            { $eq: [ '$%s', { $convert: { input: '$$localVal', to: 'objectId', onError: null, onNull: null } } ] }, " +
                        "            { $eq: [ { $toString: '$%s' }, '$$localVal' ] }, " +
                        "            { $eq: [ '$%s', { $toString: '$$localVal' } ] } " +
                        "          ] " +
                        "        } " +
                        "      } " +
                        "    } " +
                        "  ], " +
                        "  as: '%s' " +
                        "} }",
                foreignCollection, localField, foreignField, foreignField, foreignField, foreignField, alias);
        return context -> org.bson.Document.parse(lookupJson);
    }

    public List<Map> ejecutarConsulta(ReporteResponseDto definicion) {
        if (!catalogoService.esEntidadPermitida(definicion.getEntidadPrincipal())) {
            throw new IllegalArgumentException("Entidad no permitida: " + definicion.getEntidadPrincipal());
        }

        List<AggregationOperation> operations = new ArrayList<>();
        com.leo.politicas_de_negocio.reportes.model.EntidadReportable entidadReportable = catalogoService
                .obtenerEntidadPorNombreOAlias(definicion.getEntidadPrincipal());

        // 0. Unwinds previos requeridos por los campos locales de tipo array
        if (entidadReportable != null && entidadReportable.getCampos() != null) {
            List<String> arrayFieldsToUnwind = new ArrayList<>();
            List<String> allRequestedFields = new ArrayList<>();
            if (definicion.getCampos() != null)
                allRequestedFields.addAll(definicion.getCampos());
            if (definicion.getAgrupaciones() != null)
                allRequestedFields.addAll(definicion.getAgrupaciones());
            if (definicion.getFiltros() != null) {
                for (FiltroDto f : definicion.getFiltros()) {
                    allRequestedFields.add(f.getCampo());
                }
            }

            for (String f : allRequestedFields) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                        f);
                String fieldToCheck = (rf != null) ? rf.getTargetFieldName() : f;
                com.leo.politicas_de_negocio.reportes.model.CampoReportable c = catalogoService
                        .obtenerCampoDeEntidad(entidadReportable, fieldToCheck);
                if (c != null && c.isRequiereUnwind()) {
                    String rootArray = c.getCampoMongo().split("\\.")[0];
                    if (!arrayFieldsToUnwind.contains(rootArray)) {
                        arrayFieldsToUnwind.add(rootArray);
                    }
                }
            }

            for (String arrayName : arrayFieldsToUnwind) {
                operations.add(Aggregation.unwind(arrayName, true));
            }
        }

        // --- RESOLVER CAMPOS Y PREPARAR LOOKUPS DINÁMICOS ---
        Set<String> referencedFields = new LinkedHashSet<>();
        if (definicion.getCampos() != null)
            referencedFields.addAll(definicion.getCampos());
        if (definicion.getAgrupaciones() != null)
            referencedFields.addAll(definicion.getAgrupaciones());
        if (definicion.getFiltros() != null) {
            for (FiltroDto f : definicion.getFiltros()) {
                referencedFields.add(f.getCampo());
            }
        }
        if (definicion.getMetricas() != null) {
            for (MetricaDto m : definicion.getMetricas()) {
                referencedFields.add(m.getCampo());
            }
        }
        if (definicion.getOrdenamiento() != null) {
            for (OrdenamientoDto o : definicion.getOrdenamiento()) {
                referencedFields.add(o.getCampo());
            }
        }

        Map<String, LookupInfo> uniqueLookups = new LinkedHashMap<>();
        for (String field : referencedFields) {
            ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                    field);
            if (rf != null && rf.getPath() != null && !rf.getPath().isEmpty()) {
                List<ReporteRelacionGraphService.RelationshipStep> path = rf.getPath();
                String currentPrefix = "";
                for (int i = 0; i < path.size(); i++) {
                    ReporteRelacionGraphService.RelationshipStep step = path.get(i);
                    String stepAlias = ReporteCampoResolver.construirAliasPath(path.subList(0, i + 1));
                    String localFieldWithPrefix = (i == 0) ? step.getLocalField()
                            : currentPrefix + "." + step.getLocalField();
                    uniqueLookups.put(stepAlias, new LookupInfo(step, localFieldWithPrefix, stepAlias));
                    currentPrefix = stepAlias;
                }
            }
        }

        // --- APLICAR MATCH DE CAMPOS LOCALES ANTES DE LOOKUPS (OPTIMIZACIÓN) ---
        if (definicion.getFiltros() != null && !definicion.getFiltros().isEmpty()) {
            for (FiltroDto filtro : definicion.getFiltros()) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                        filtro.getCampo());
                if (rf == null) {
                    throw new IllegalArgumentException("Campo no permitido en filtro: " + filtro.getCampo());
                }
                if (rf.getPath().isEmpty()) {
                    Criteria c = Criteria.where(rf.getResolvedMongoPath());
                    aplicarOperadorFiltro(c, filtro);
                    operations.add(Aggregation.match(c));
                }
            }
        }

        // --- APLICAR LOOKUPS SEGUROS Y UNWINDS ---
        for (LookupInfo info : uniqueLookups.values()) {
            String fromCollection = info.getStep().getFromCollection();
            String foreignField = info.getStep().getForeignField();
            String alias = info.getAliasPath();

            // Usar lookup seguro que tolera ObjectId y String
            operations.add(crearLookupSeguro(info.getLocalFieldWithPrefix(), fromCollection, foreignField, alias));
            operations.add(Aggregation.unwind(alias, true));
        }

        // --- MATERIALIZAR CAMPOS NO LOCALES AL NIVEL RAÍZ ---
        org.bson.Document addFieldsDoc = new org.bson.Document();
        for (String field : referencedFields) {
            ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                    field);
            if (rf != null) {
                log.info("DEBUG RESOLVED FIELD: {} -> original: {}, mongoPath: {}, isDerived: {}",
                        field, rf.getOriginalFieldName(), rf.getResolvedMongoPath(), rf.isDerived());
            }
            if (rf != null && (rf.isDerived() || !rf.getOriginalFieldName().equals(rf.getResolvedMongoPath()))
                    && rf.getResolvedMongoPath() != null) {
                if (rf.getOriginalFieldName().equals("cantidadNodos")) {
                    String prefix = "";
                    if (rf.getPath() != null && !rf.getPath().isEmpty()) {
                        prefix = ReporteCampoResolver.construirAliasPath(rf.getPath()) + ".";
                    }
                    String nodosPath = "$" + prefix + "nodos";
                    addFieldsDoc.put("cantidadNodos", new org.bson.Document("$cond", Arrays.asList(
                            new org.bson.Document("$isArray", nodosPath),
                            new org.bson.Document("$size", nodosPath),
                            0)));
                } else {
                    addFieldsDoc.put(rf.getOriginalFieldName(), "$" + rf.getResolvedMongoPath());
                }
            }
        }
        if (!addFieldsDoc.isEmpty()) {
            operations.add(context -> new org.bson.Document("$addFields", addFieldsDoc));
        }

        // --- APLICAR MATCH DE CAMPOS RELACIONADOS DESPUÉS DE LOOKUPS ---
        if (definicion.getFiltros() != null && !definicion.getFiltros().isEmpty()) {
            for (FiltroDto filtro : definicion.getFiltros()) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                        filtro.getCampo());
                if (rf != null && !rf.getPath().isEmpty()) {
                    Criteria c = Criteria.where(filtro.getCampo()); // Ya materializado en raíz
                    aplicarOperadorFiltro(c, filtro);
                    operations.add(Aggregation.match(c));
                }
            }
        }

        // --- AGRUPACIONES Y MÉTRICAS ---
        if (definicion.getAgrupaciones() != null && !definicion.getAgrupaciones().isEmpty()) {
            log.info("DEFINICION AGRUPACIONES: {}", definicion.getAgrupaciones());
            log.info("DEFINICION CAMPOS: {}", definicion.getCampos());

            // Filtrar nulos/vacíos en agrupaciones antes de agrupar
            for (String groupField : definicion.getAgrupaciones()) {
                Criteria c = new Criteria().andOperator(
                        Criteria.where(groupField).ne(null),
                        Criteria.where(groupField).ne(""),
                        Criteria.where(groupField).ne("-"),
                        Criteria.where(groupField).ne("N/A"));
                operations.add(Aggregation.match(c));
            }

            for (String groupField : definicion.getAgrupaciones()) {
                ReporteCampoResolver.ResolvedField rf = campoResolver.resolverCampo(definicion.getEntidadPrincipal(),
                        groupField);
                if (rf == null) {
                    throw new IllegalArgumentException("Campo de agrupación no permitido: " + groupField);
                }
            }

            String[] groupFields = definicion.getAgrupaciones().toArray(new String[0]);
            var groupOp = Aggregation.group(groupFields);

            if (definicion.getMetricas() != null) {
                for (MetricaDto metrica : definicion.getMetricas()) {
                    String metricaField = metrica.getCampo();
                    ReporteCampoResolver.ResolvedField rf = campoResolver
                            .resolverCampo(definicion.getEntidadPrincipal(), metricaField);
                    if (rf != null) {
                        metricaField = rf.getOriginalFieldName();
                    }

                    switch (metrica.getOperacion().toLowerCase()) {
                        case "count":
                            groupOp = groupOp.count().as(metrica.getAlias());
                            break;
                        case "sum":
                            groupOp = groupOp.sum(metricaField).as(metrica.getAlias());
                            break;
                        case "avg":
                            groupOp = groupOp.avg(metricaField).as(metrica.getAlias());
                            break;
                        case "max":
                            groupOp = groupOp.max(metricaField).as(metrica.getAlias());
                            break;
                        case "min":
                            groupOp = groupOp.min(metricaField).as(metrica.getAlias());
                            break;
                        case "addtoset":
                        case "collect":
                            groupOp = groupOp.addToSet(metricaField).as(metrica.getAlias());
                            break;
                    }
                }
            }
            operations.add(groupOp);

            // Re-proyectar desde _id al nombre original del campo agrupador
            if (definicion.getAgrupaciones().size() == 1) {
                String groupField = definicion.getAgrupaciones().get(0);
                org.bson.Document projectDoc = new org.bson.Document();
                projectDoc.put("_id", 0);
                projectDoc.put(groupField, "$_id");

                if (definicion.getMetricas() != null) {
                    for (MetricaDto metrica : definicion.getMetricas()) {
                        projectDoc.put(metrica.getAlias(), 1);
                    }
                }
                operations.add(context -> new org.bson.Document("$project", projectDoc));
            } else {
                org.bson.Document projectDoc = new org.bson.Document();
                projectDoc.put("_id", 0);
                for (String groupField : definicion.getAgrupaciones()) {
                    projectDoc.put(groupField, "$_id." + groupField);
                }
                if (definicion.getMetricas() != null) {
                    for (MetricaDto metrica : definicion.getMetricas()) {
                        projectDoc.put(metrica.getAlias(), 1);
                    }
                }
                operations.add(context -> new org.bson.Document("$project", projectDoc));
            }

            // Enriquecer sets acumulados (addToSet de IDs)
            if (definicion.getMetricas() != null && entidadReportable != null
                    && entidadReportable.getRelaciones() != null) {
                for (MetricaDto metrica : definicion.getMetricas()) {
                    if (metrica.getOperacion().equalsIgnoreCase("addtoset")
                            || metrica.getOperacion().equalsIgnoreCase("collect")) {
                        for (com.leo.politicas_de_negocio.reportes.model.RelacionReportable rel : entidadReportable
                                .getRelaciones()) {
                            if (rel.getCampoLocal().equals(metrica.getCampo()) && rel.getCamposEnriquecidos() != null
                                    && !rel.getCamposEnriquecidos().isEmpty()) {
                                String aliasArray = metrica.getAlias();
                                String targetCollection = rel.getEntidadDestino();
                                String enrField = rel.getCamposEnriquecidos().get(0);
                                String logicalName = aliasArray + "Nombres";

                                String lookupStr = "{ $lookup: { " +
                                        "from: '" + targetCollection + "', " +
                                        "let: { idsArray: { $ifNull: ['$" + aliasArray + "', []] } }, " +
                                        "pipeline: [ " +
                                        "{ $match: { $expr: { $in: [ { $toString: '$_id' }, '$$idsArray' ] } } }, " +
                                        "{ $project: { " + enrField + ": 1, _id: 0 } } " +
                                        "], " +
                                        "as: '" + aliasArray + "Detalle' " +
                                        "} }";

                                operations.add(context -> org.bson.Document.parse(lookupStr));

                                String setFieldStr = "{ $addFields: { " +
                                        logicalName + ": { " +
                                        "$map: { " +
                                        "input: '$" + aliasArray + "Detalle', " +
                                        "as: 'item', " +
                                        "in: '$$item." + enrField + "' " +
                                        "} " +
                                        "} " +
                                        "} }";

                                operations.add(context -> org.bson.Document.parse(setFieldStr));

                                String unsetStr = "{ $project: { '" + aliasArray + "Detalle': 0 } }";
                                operations.add(context -> org.bson.Document.parse(unsetStr));
                                break;
                            }
                        }
                    }
                }
            }

        } else {
            // LISTADO SIMPLE
            org.springframework.data.mongodb.core.aggregation.ProjectionOperation proj = Aggregation.project()
                    .andExclude("_id");
            if (definicion.getCampos() != null && !definicion.getCampos().isEmpty()) {
                for (String campo : definicion.getCampos()) {
                    ReporteCampoResolver.ResolvedField rf = campoResolver
                            .resolverCampo(definicion.getEntidadPrincipal(), campo);
                    if (rf == null) {
                        throw new IllegalArgumentException("Campo solicitado no permitido: " + campo);
                    }
                    proj = proj.andInclude(campo);
                }
            } else {
                if (entidadReportable != null && entidadReportable.getCampos() != null) {
                    for (var c : entidadReportable.getCampos()) {
                        if (c.isReportable() && !c.isSensible()) {
                            proj = proj.andInclude(c.getNombreLogico());
                        }
                    }
                }
            }
            operations.add(proj);
        }

        // --- ORDENAMIENTO ---
        if (definicion.getOrdenamiento() != null && !definicion.getOrdenamiento().isEmpty()) {
            org.bson.Document sortDoc = new org.bson.Document();
            for (OrdenamientoDto o : definicion.getOrdenamiento()) {
                int dir = o.getDireccion().equalsIgnoreCase("desc") ? -1 : 1;
                sortDoc.put(o.getCampo(), dir);
            }
            operations.add(context -> new org.bson.Document("$sort", sortDoc));
        }

        // --- LÍMITE ---
        int limit = (definicion.getLimite() != null && definicion.getLimite() > 0) ? definicion.getLimite() : 100;
        if (limit > 5000)
            limit = 5000;
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        log.info("Pipeline generado para {}: {}", definicion.getEntidadPrincipal(), aggregation.toString());

        List<Map> results = mongoTemplate.aggregate(aggregation, definicion.getEntidadPrincipal(), Map.class).getMappedResults();
        log.info("Resultados de la consulta para {}: {}", definicion.getEntidadPrincipal(), results);
        return results;
    }

    private void aplicarOperadorFiltro(Criteria c, FiltroDto filtro) {
        switch (filtro.getOperador().toLowerCase()) {
            case "=":
                c.is(filtro.getValor());
                break;
            case "!=":
                c.ne(filtro.getValor());
                break;
            case ">":
                c.gt(filtro.getValor());
                break;
            case ">=":
                c.gte(filtro.getValor());
                break;
            case "<":
                c.lt(filtro.getValor());
                break;
            case "<=":
                c.lte(filtro.getValor());
                break;
            case "in":
            case "en_lista":
                if (filtro.getValor() instanceof Collection) {
                    c.in((Collection<?>) filtro.getValor());
                } else if (filtro.getValor() != null && filtro.getValor().getClass().isArray()) {
                    c.in((Object[]) filtro.getValor());
                } else {
                    c.in(filtro.getValor());
                }
                break;
            case "contains":
            case "contiene":
                if (filtro.getValor() != null) {
                    c.regex(".*" + filtro.getValor().toString() + ".*", "i");
                }
                break;
            case "mes_actual":
                c.gte(LocalDate.now().withDayOfMonth(1).atStartOfDay());
                break;
            case "anio_actual":
                c.gte(LocalDate.now().withDayOfYear(1).atStartOfDay());
                break;
            case "ultimos_dias":
                int dias = filtro.getValor() != null ? Integer.parseInt(filtro.getValor().toString()) : 7;
                c.gte(LocalDateTime.now().minusDays(dias));
                break;
            case "ultimos_meses":
                int meses = filtro.getValor() != null ? Integer.parseInt(filtro.getValor().toString()) : 3;
                c.gte(LocalDateTime.now().minusMonths(meses));
                break;
            default:
                throw new IllegalArgumentException("Operador no soportado: " + filtro.getOperador());
        }
    }
}
