package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.FiltroDto;
import com.leo.politicas_de_negocio.reportes.dto.MetricaDto;
import com.leo.politicas_de_negocio.reportes.dto.OrdenamientoDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReporteMongoAggregationBuilder {

    private final MongoTemplate mongoTemplate;
    private final ReporteCatalogoService catalogoService;

    public List<Map> ejecutarConsulta(ReporteResponseDto definicion) {
        if (!catalogoService.esEntidadPermitida(definicion.getEntidadPrincipal())) {
            throw new IllegalArgumentException("Entidad no permitida: " + definicion.getEntidadPrincipal());
        }

        List<AggregationOperation> operations = new ArrayList<>();

        // 1. Filtros (Match)
        if (definicion.getFiltros() != null && !definicion.getFiltros().isEmpty()) {
            Criteria criteria = new Criteria();
            for (FiltroDto filtro : definicion.getFiltros()) {
                if (!catalogoService.esCampoPermitido(definicion.getEntidadPrincipal(), filtro.getCampo())) {
                    throw new IllegalArgumentException("Campo no permitido en filtro: " + filtro.getCampo());
                }
                
                Criteria c = Criteria.where(filtro.getCampo());
                switch (filtro.getOperador().toLowerCase()) {
                    case "=": c.is(filtro.getValor()); break;
                    case "!=": c.ne(filtro.getValor()); break;
                    case ">": c.gt(filtro.getValor()); break;
                    case ">=": c.gte(filtro.getValor()); break;
                    case "<": c.lt(filtro.getValor()); break;
                    case "<=": c.lte(filtro.getValor()); break;
                    case "mes_actual":
                        LocalDateTime inicioMes = LocalDate.now().withDayOfMonth(1).atStartOfDay();
                        c.gte(inicioMes);
                        break;
                    case "anio_actual":
                        LocalDateTime inicioAnio = LocalDate.now().withDayOfYear(1).atStartOfDay();
                        c.gte(inicioAnio);
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
                operations.add(Aggregation.match(c));
            }
        }

        // 2. Agrupaciones y Métricas
        if (definicion.getAgrupaciones() != null && !definicion.getAgrupaciones().isEmpty()) {
            String[] groupFields = definicion.getAgrupaciones().toArray(new String[0]);
            var groupOp = Aggregation.group(groupFields);
            
            if (definicion.getMetricas() != null) {
                for (MetricaDto metrica : definicion.getMetricas()) {
                    switch (metrica.getOperacion().toLowerCase()) {
                        case "count":
                            groupOp = groupOp.count().as(metrica.getAlias());
                            break;
                        case "sum":
                            groupOp = groupOp.sum(metrica.getCampo()).as(metrica.getAlias());
                            break;
                        case "avg":
                            groupOp = groupOp.avg(metrica.getCampo()).as(metrica.getAlias());
                            break;
                        case "max":
                            groupOp = groupOp.max(metrica.getCampo()).as(metrica.getAlias());
                            break;
                        case "min":
                            groupOp = groupOp.min(metrica.getCampo()).as(metrica.getAlias());
                            break;
                    }
                }
            }
            operations.add(groupOp);
        }

        // 3. Ordenamiento
        if (definicion.getOrdenamiento() != null && !definicion.getOrdenamiento().isEmpty()) {
            List<Sort.Order> orders = new ArrayList<>();
            for (OrdenamientoDto o : definicion.getOrdenamiento()) {
                Sort.Direction dir = o.getDireccion().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
                orders.add(new Sort.Order(dir, o.getCampo()));
            }
            operations.add(Aggregation.sort(Sort.by(orders)));
        }

        // 4. Límite
        int limit = (definicion.getLimite() != null && definicion.getLimite() > 0) ? definicion.getLimite() : 100;
        if (limit > 5000) limit = 5000;
        operations.add(Aggregation.limit(limit));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        return mongoTemplate.aggregate(aggregation, definicion.getEntidadPrincipal(), Map.class).getMappedResults();
    }
}
