package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.ResultadoBloqueReporteDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class ReporteVisualMapper {

    public ResultadoBloqueReporteDTO mapear(String tipoBloque, List<Map> registros) {
        if (registros == null || registros.isEmpty()) {
            return ResultadoBloqueReporteDTO.builder()
                    .labels(new ArrayList<>())
                    .values(new ArrayList<>())
                    .columns(new ArrayList<>())
                    .rows(new ArrayList<>())
                    .build();
        }

        log.info("Mapeando {} registros para bloque de tipo: {}", registros.size(), tipoBloque);

        List<String> labels = new ArrayList<>();
        List<Number> values = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        // 1. Si es tipo KPI, extraer el único número principal del primer registro
        if ("kpi".equalsIgnoreCase(tipoBloque)) {
            Map firstRow = registros.get(0);
            Number value = 0;
            String label = "Total";
            for (Object key : firstRow.keySet()) {
                Object val = firstRow.get(key);
                if (val instanceof Number) {
                    value = (Number) val;
                    label = String.valueOf(key);
                    break;
                }
            }
            labels.add(label);
            values.add(value);
        }
        // 2. Si es tabla o matriz, extraer columnas dinámicas y filas correspondientes
        else if ("table".equalsIgnoreCase(tipoBloque) || "matrix".equalsIgnoreCase(tipoBloque)) {
            // Extraer nombres de columna del primer registro
            Map firstRow = registros.get(0);
            for (Object key : firstRow.keySet()) {
                String colName = String.valueOf(key);
                // Omitir columnas técnicas si existieran
                if (!"tokensJoin".equals(colName) && !"datosContexto".equals(colName) && !colName.startsWith("_")) {
                    columns.add(colName);
                }
            }

            for (Map reg : registros) {
                List<Object> row = new ArrayList<>();
                for (String col : columns) {
                    row.add(reg.get(col));
                }
                rows.add(row);
            }
        }
        // 3. Si es un gráfico (bar, pie, doughnut, line, area), buscar la clave String y la clave Number
        else {
            Map firstRow = registros.get(0);
            String labelKey = null;
            String valueKey = null;

            for (Object key : firstRow.keySet()) {
                String keyStr = String.valueOf(key);
                if (keyStr.startsWith("_") || "tokensJoin".equals(keyStr) || "datosContexto".equals(keyStr)) {
                    continue;
                }
                Object val = firstRow.get(key);
                if (val instanceof Number) {
                    valueKey = keyStr;
                } else if (val != null) {
                    labelKey = keyStr;
                }
            }

            // Si no se encuentra un labelKey pero hay al menos dos columnas, usar la primera no-númerica
            if (labelKey == null && firstRow.size() > 1) {
                for (Object key : firstRow.keySet()) {
                    String keyStr = String.valueOf(key);
                    if (keyStr.startsWith("_") || "tokensJoin".equals(keyStr) || "datosContexto".equals(keyStr)) {
                        continue;
                    }
                    if (!keyStr.equals(valueKey)) {
                        labelKey = keyStr;
                        break;
                    }
                }
            }

            for (Map reg : registros) {
                Object labelVal = labelKey != null ? reg.get(labelKey) : "N/A";
                Object valueVal = valueKey != null ? reg.get(valueKey) : 0;

                labels.add(labelVal != null ? labelVal.toString() : "N/A");
                values.add(valueVal instanceof Number ? (Number) valueVal : 0);
            }
        }

        return ResultadoBloqueReporteDTO.builder()
                .labels(labels)
                .values(values)
                .columns(columns)
                .rows(rows)
                .build();
    }
}
