package com.leo.politicas_de_negocio.reportes.offline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LocalDynamicReportEngine {

    @SuppressWarnings("unchecked")
    public List<Map> ejecutarConsultaMetricaLocal(
            String metrica,
            String entidadPrincipal,
            Map<String, Object> filtros,
            int limite,
            Map<String, Object> snapshot) {
        
        log.info("LocalDynamicReportEngine: Calculando métrica local '{}' para entidad '{}'. Filtros: {}, Limite: {}", 
                metrica, entidadPrincipal, filtros, limite);

        List<Map<String, Object>> rawCollection = (List<Map<String, Object>>) snapshot.get(entidadPrincipal);
        if (rawCollection == null) {
            rawCollection = new ArrayList<>();
        }

        // Aplicar filtro temporal global a entidades operacionales offline (abril-junio 2026)
        if ("instancias_politica".equalsIgnoreCase(entidadPrincipal) || "tareas_actividad".equalsIgnoreCase(entidadPrincipal)) {
            rawCollection = aplicarFiltroFechaGlobal(rawCollection);
        }

        // 1. Aplicar filtros en memoria
        List<Map<String, Object>> filtered = aplicarFiltros(rawCollection, filtros);

        // 2. Agrupar y Agregar según la métrica correspondiente
        List<Map> aggregated = agruparYAgregar(filtered, metrica, snapshot);

        // 3. Limitar cantidad de filas según lo solicitado
        int maxLimit = limite > 0 ? limite : 10;
        return aggregated.stream()
                .limit(maxLimit)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> aplicarFiltroFechaGlobal(List<Map<String, Object>> coleccion) {
        LocalDateTime minDate = LocalDateTime.of(2026, 4, 1, 0, 0, 0);
        LocalDateTime maxDate = LocalDateTime.of(2026, 6, 30, 23, 59, 59);

        return coleccion.stream()
                .filter(item -> {
                    Object fVal = item.get("fechaCreacion");
                    if (fVal == null) {
                        return true;
                    }
                    LocalDateTime date = parseDate(fVal);
                    if (date == null) {
                        return true;
                    }
                    return !date.isBefore(minDate) && !date.isAfter(maxDate);
                })
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map> agruparYAgregar(List<Map<String, Object>> registros, String metrica, Map<String, Object> snapshot) {
        List<Map> result = new ArrayList<>();

        switch (metrica.toLowerCase()) {
            case "tramites_por_mes": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("fechaCreacion") != null)
                        .collect(Collectors.groupingBy(
                                r -> {
                                    LocalDateTime date = parseDate(r.get("fechaCreacion"));
                                    if (date == null) return "Unknown";
                                    return String.format("%04d-%02d", date.getYear(), date.getMonthValue());
                                },
                                Collectors.counting()
                        ));

                groups.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Mes", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "promedio_tiempo_finalizacion": {
                List<Map<String, Object>> finalizados = registros.stream()
                        .filter(r -> "FINALIZADA".equalsIgnoreCase(String.valueOf(r.get("estadoInstancia"))))
                        .filter(r -> r.get("fechaCreacion") != null && r.get("fechaFinalizacion") != null)
                        .toList();

                double sumDays = 0.0;
                int count = 0;
                for (Map<String, Object> r : finalizados) {
                    LocalDateTime creacion = parseDate(r.get("fechaCreacion"));
                    LocalDateTime finalizacion = parseDate(r.get("fechaFinalizacion"));
                    if (creacion != null && finalizacion != null && !finalizacion.isBefore(creacion)) {
                        double days = Duration.between(creacion, finalizacion).toMillis() / 86400000.0;
                        sumDays += days;
                        count++;
                    }
                }

                double avg = count == 0 ? 0.0 : sumDays / count;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Promedio de días", Math.round(avg * 100.0) / 100.0);
                result.add(row);
                break;
            }

            case "funcionarios_mas_activos": {
                Map<String, Long> groups = registros.stream()
                        .map(r -> lookupNombreUsuario(String.valueOf(r.get("funcionarioAsignado")), snapshot))
                        .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Funcionario", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "clientes_mas_inician_politicas": {
                Map<String, Long> groups = registros.stream()
                        .map(r -> lookupNombreUsuario(String.valueOf(r.get("creadaPor")), snapshot))
                        .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Cliente", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "administradores_mas_politicas_crearon": {
                Map<String, Long> groups = registros.stream()
                        .map(r -> lookupNombreUsuario(String.valueOf(r.get("creadaPor")), snapshot))
                        .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Administrador", entry.getKey());
                            row.put("Políticas creadas", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "politicas_mas_usadas": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("politicaNombre") != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get("politicaNombre")), Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Política", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "politicas_por_estado": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("estado") != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get("estado")), Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Estado", entry.getKey());
                            row.put("Cantidad de políticas", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "tramites_por_estado": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("estadoInstancia") != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get("estadoInstancia")), Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Estado", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "tramites_por_departamento": {
                Map<String, Long> groups = registros.stream()
                        .map(r -> lookupNombreDepartamento(String.valueOf(r.get("departamentoId")), snapshot))
                        .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Departamento", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "tramites_por_prioridad": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("prioridad") != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get("prioridad")), Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Prioridad", entry.getKey());
                            row.put("Cantidad de trámites", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "tramites_finalizados_por_funcionario": {
                List<Map<String, Object>> finalizados = registros.stream()
                        .filter(r -> "FINALIZADA".equalsIgnoreCase(String.valueOf(r.get("estadoInstancia"))))
                        .toList();

                Map<String, Long> groups = finalizados.stream()
                        .map(r -> lookupNombreUsuario(String.valueOf(r.get("funcionarioAsignado")), snapshot))
                        .collect(Collectors.groupingBy(name -> name, Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Funcionario", entry.getKey());
                            row.put("Cantidad de trámites finalizados", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "pagos_por_estado": {
                Map<String, Long> groups = registros.stream()
                        .filter(r -> r.get("estado") != null)
                        .collect(Collectors.groupingBy(r -> String.valueOf(r.get("estado")), Collectors.counting()));

                groups.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Estado", entry.getKey());
                            row.put("Cantidad de pagos", entry.getValue());
                            result.add(row);
                        });
                break;
            }

            case "pagos_por_politica": {
                Map<String, DoubleSummaryStatistics> groups = registros.stream()
                        .filter(r -> r.get("politicaId") != null && r.get("monto") != null)
                        .collect(Collectors.groupingBy(
                                r -> lookupNombrePolitica(String.valueOf(r.get("politicaId")), snapshot),
                                Collectors.summarizingDouble(r -> ((Number) r.get("monto")).doubleValue())
                        ));

                groups.entrySet().stream()
                        .sorted((e1, e2) -> Double.compare(e2.getValue().getSum(), e1.getValue().getSum()))
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("Política", entry.getKey());
                            row.put("Total recaudado", Math.round(entry.getValue().getSum() * 100.0) / 100.0);
                            result.add(row);
                        });
                break;
            }

            case "total_tramites": {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Total trámites", (long) registros.size());
                result.add(row);
                break;
            }

            case "total_politicas": {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Total políticas", (long) registros.size());
                result.add(row);
                break;
            }

            case "total_usuarios": {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Total usuarios", (long) registros.size());
                result.add(row);
                break;
            }

            case "total_pagos": {
                double total = registros.stream()
                        .filter(r -> r.get("monto") != null)
                        .mapToDouble(r -> ((Number) r.get("monto")).doubleValue())
                        .sum();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("Total recaudado", Math.round(total * 100.0) / 100.0);
                result.add(row);
                break;
            }

            case "cuellos_botella": {
                List<Map<String, Object>> tareas = (List<Map<String, Object>>) snapshot.get("tareas_actividad");
                if (tareas == null) tareas = new ArrayList<>();

                LocalDateTime now = LocalDateTime.now();
                
                Map<String, Long> retrasosPorActividad = tareas.stream()
                        .filter(t -> ! "COMPLETADA".equalsIgnoreCase(String.valueOf(t.get("estado"))))
                        .filter(t -> t.get("fechaLimite") != null && t.get("actividadNombre") != null)
                        .filter(t -> {
                            LocalDateTime limite = parseDate(t.get("fechaLimite"));
                            return limite != null && limite.isBefore(now);
                        })
                        .collect(Collectors.groupingBy(t -> String.valueOf(t.get("actividadNombre")), Collectors.counting()));

                if (retrasosPorActividad.isEmpty()) {
                    retrasosPorActividad = tareas.stream()
                            .filter(t -> ! "COMPLETADA".equalsIgnoreCase(String.valueOf(t.get("estado"))))
                            .filter(t -> t.get("actividadNombre") != null)
                            .collect(Collectors.groupingBy(t -> String.valueOf(t.get("actividadNombre")), Collectors.counting()));
                }

                retrasosPorActividad.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("tipo", "ACTIVIDAD");
                            row.put("nombre", entry.getKey());
                            row.put("severidad", entry.getValue() > 5 ? "ALTO" : "MEDIO");
                            row.put("evidencia", entry.getValue() + " tareas demoradas en modo offline");
                            row.put("impacto", "Retraso en la finalización de los trámites que involucran esta actividad");
                            row.put("recomendacion", "Redistribuir la carga de tareas de esta actividad o reasignar funcionarios");
                            result.add(row);
                        });
                break;
            }

            default:
                log.warn("Métrica local no soportada: {}", metrica);
                break;
        }

        return result;
    }

    private List<Map<String, Object>> aplicarFiltros(List<Map<String, Object>> list, Map<String, Object> filtros) {
        if (filtros == null || filtros.isEmpty()) {
            return list;
        }
        return list.stream()
                .filter(reg -> {
                    for (Map.Entry<String, Object> entry : filtros.entrySet()) {
                        String campo = entry.getKey();
                        Object filtroVal = entry.getValue();

                        Object targetValor = filtroVal;
                        String operador = "=";
                        if (filtroVal instanceof Map) {
                            Map<?, ?> mapVal = (Map<?, ?>) filtroVal;
                            if (mapVal.containsKey("operador")) {
                                operador = String.valueOf(mapVal.get("operador"));
                                targetValor = mapVal.get("valor");
                            }
                        }

                        if ("mesActual".equals(campo)) {
                            Object fVal = reg.get("fechaCreacion");
                            LocalDateTime date = parseDate(fVal);
                            if (date == null) return false;
                            // En modo offline demo, "mesActual" se fuerza a junio de 2026
                            if (date.getYear() != 2026 || date.getMonthValue() != 6) {
                                return false;
                            }
                            continue;
                        }

                        Object regVal = reg.get(campo);
                        if (regVal == null) {
                            return false;
                        }

                        if ("=".equals(operador)) {
                            if (!String.valueOf(regVal).equalsIgnoreCase(String.valueOf(targetValor))) {
                                return false;
                            }
                        } else if ("!=".equals(operador)) {
                            if (String.valueOf(regVal).equalsIgnoreCase(String.valueOf(targetValor))) {
                                return false;
                            }
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDate(Object val) {
        if (val == null) return null;
        if (val instanceof Number) {
            long epoch = ((Number) val).longValue();
            return java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        }
        if (val instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) val;
            if (map.containsKey("$date")) {
                Object dateVal = map.get("$date");
                if (dateVal instanceof Number) {
                    long epoch = ((Number) dateVal).longValue();
                    return java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
                } else if (dateVal != null) {
                    return parseDate(dateVal.toString());
                }
            }
        }
        String str = val.toString().trim();
        if (str.isEmpty()) return null;
        if (str.matches("^\\d+$")) {
            try {
                long epoch = Long.parseLong(str);
                return java.time.Instant.ofEpochMilli(epoch).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception e) {
                // ignore
            }
        }
        try {
            return LocalDateTime.parse(str);
        } catch (Exception e) {
            try {
                return java.time.OffsetDateTime.parse(str).toLocalDateTime();
            } catch (Exception ex) {
                try {
                    return java.time.ZonedDateTime.parse(str).toLocalDateTime();
                } catch (Exception ex2) {
                    try {
                        return java.time.LocalDate.parse(str).atStartOfDay();
                    } catch (Exception ex3) {
                        log.warn("No se pudo parsear la fecha: {}", val);
                        return null;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String lookupNombreUsuario(String userId, Map<String, Object> snapshot) {
        if (userId == null || "null".equals(userId)) return "Desconocido";
        List<Map<String, Object>> usuarios = (List<Map<String, Object>>) snapshot.get("usuarios");
        if (usuarios == null) return "Desconocido";
        return usuarios.stream()
                .filter(u -> userId.equals(String.valueOf(u.get("id"))))
                .map(u -> (String) u.get("nombre"))
                .findFirst()
                .orElse("Desconocido");
    }

    @SuppressWarnings("unchecked")
    private String lookupNombrePolitica(String politicaId, Map<String, Object> snapshot) {
        if (politicaId == null || "null".equals(politicaId)) return "Desconocida";
        List<Map<String, Object>> politicas = (List<Map<String, Object>>) snapshot.get("politicas_negocio");
        if (politicas == null) return "Desconocida";
        return politicas.stream()
                .filter(p -> politicaId.equals(String.valueOf(p.get("id"))))
                .map(p -> (String) p.get("nombre"))
                .findFirst()
                .orElse("Desconocida");
    }

    @SuppressWarnings("unchecked")
    private String lookupNombreDepartamento(String departamentoId, Map<String, Object> snapshot) {
        if (departamentoId == null || "null".equals(departamentoId)) return "Desconocido";
        List<Map<String, Object>> departamentos = (List<Map<String, Object>>) snapshot.get("departamentos");
        if (departamentos == null) return "Desconocido";
        return departamentos.stream()
                .filter(d -> departamentoId.equals(String.valueOf(d.get("id"))))
                .map(d -> (String) d.get("nombre"))
                .findFirst()
                .orElse("Desconocido");
    }
}
