package com.leo.politicas_de_negocio.reportes.offline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineReportSyncDataService {

    private final MongoTemplate mongoTemplate;
    private final OfflineReportLocalDataService localDataService;

    @Value("${offline.reports.sync.max-instancias:500}")
    private int maxInstancias;

    @Value("${offline.reports.sync.max-tareas:500}")
    private int maxTareas;

    @Value("${offline.reports.sync.max-pagos:500}")
    private int maxPagos;

    @Value("${offline.reports.sync.days-back:30}")
    private int daysBack;

    @SuppressWarnings("unchecked")
    public Map<String, Object> sincronizarDatosOffline() {
        log.info("== INICIANDO SINCRONIZACIÓN OFFLINE PARA REPORTES ==");
        log.info("Parámetros: max-instancias={}, max-tareas={}, max-pagos={}, days-back={}", 
                maxInstancias, maxTareas, maxPagos, daysBack);

        LocalDateTime cutOffDate = LocalDateTime.now().minusDays(daysBack);
        List<String> warnings = new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();

        // 1. Instancias de política
        List<Map<String, Object>> instanciasRaw = fetchCollectionWithTimeFilter("instancias_politica", "fechaCreacion", cutOffDate, maxInstancias);
        List<Map<String, Object>> instancias = instanciasRaw.stream().map(this::sanitizarInstancia).collect(Collectors.toList());
        counts.put("instancias_politica", instancias.size());

        // 2. Tareas de actividad
        List<Map<String, Object>> tareasRaw = fetchCollectionWithTimeFilter("tareas_actividad", "fechaCreacion", cutOffDate, maxTareas);
        List<Map<String, Object>> tareas = tareasRaw.stream().map(this::sanitizarTarea).collect(Collectors.toList());
        counts.put("tareas_actividad", tareas.size());

        // 3. Pagos
        List<Map<String, Object>> pagosRaw = fetchCollectionWithTimeFilter("pagos", "fechaCreacion", cutOffDate, maxPagos);
        List<Map<String, Object>> pagos = pagosRaw.stream().map(this::sanitizarPago).collect(Collectors.toList());
        counts.put("pagos", pagos.size());

        // 4. Catálogos estáticos (sin filtro de fecha, limitados a 1000)
        List<Map<String, Object>> usuarios = fetchCollectionAll("usuarios", 1000).stream().map(this::sanitizarUsuario).collect(Collectors.toList());
        counts.put("usuarios", usuarios.size());

        List<Map<String, Object>> politicas = fetchCollectionAll("politicas_negocio", 1000).stream().map(this::sanitizarPolitica).collect(Collectors.toList());
        counts.put("politicas_negocio", politicas.size());

        List<Map<String, Object>> departamentos = fetchCollectionAll("departamentos", 200).stream().map(this::sanitizarDepartamento).collect(Collectors.toList());
        counts.put("departamentos", departamentos.size());

        // 5. Metadatos e Información de Auditoría
        Map<String, Object> snapshot = new HashMap<>();
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedAt", LocalDateTime.now().toString());
        metadata.put("lastSyncAt", LocalDateTime.now().toString());
        metadata.put("source", "MongoDB Atlas (Production Cloud)");
        metadata.put("mode", "OFFLINE_REAL_DATA_DEEP_LEARNING_LOCAL");
        metadata.put("version", "1.0.0");
        metadata.put("rangoFechas", String.format("%s - %s", cutOffDate.format(DateTimeFormatter.ISO_LOCAL_DATE), LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
        metadata.put("counts", counts);
        metadata.put("warnings", warnings);

        snapshot.put("metadata", metadata);
        snapshot.put("instancias_politica", instancias);
        snapshot.put("tareas_actividad", tareas);
        snapshot.put("pagos", pagos);
        snapshot.put("usuarios", usuarios);
        snapshot.put("politicas_negocio", politicas);
        snapshot.put("departamentos", departamentos);

        // Guardar localmente
        localDataService.saveSnapshot(snapshot);

        log.info("== SINCRONIZACIÓN COMPLETADA. Snapshot guardado conteniendo {} instancias, {} tareas, {} pagos. ==", 
                instancias.size(), tareas.size(), pagos.size());
        
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCollectionWithTimeFilter(String collectionName, String dateField, LocalDateTime cutOffDate, int limit) {
        try {
            Query query = new Query();
            query.addCriteria(Criteria.where(dateField).gte(cutOffDate));
            query.with(Sort.by(Sort.Direction.DESC, dateField));
            query.limit(limit);

            List<Map> results = mongoTemplate.find(query, Map.class, collectionName);
            return results.stream().map(m -> (Map<String, Object>) m).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al sincronizar colección {}: {}", collectionName, e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCollectionAll(String collectionName, int limit) {
        try {
            Query query = new Query();
            query.limit(limit);
            List<Map> results = mongoTemplate.find(query, Map.class, collectionName);
            return results.stream().map(m -> (Map<String, Object>) m).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error al sincronizar colección de catálogo {}: {}", collectionName, e.getMessage());
            return new ArrayList<>();
        }
    }

    // --- MÉTODOS DE SANITIZACIÓN ---

    private Map<String, Object> sanitizarInstancia(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "codigoTramite", "estadoInstancia", "fechaCreacion", "fechaFinalizacion", 
                            "creadaPor", "departamentoId", "departamentoActual", "politicaId", "politicaNombre", 
                            "funcionarioAsignado", "requierePago", "prioridad", "estado"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private Map<String, Object> sanitizarTarea(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "instanciaId", "responsableId", "estado", "fechaCreacion", 
                            "fechaLimite", "fechaCompletado", "actividadNombre", "tipo"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private Map<String, Object> sanitizarPago(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "instanciaPoliticaId", "politicaId", "monto", "estado", 
                            "fechaCreacion", "metodoPago"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private Map<String, Object> sanitizarUsuario(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "nombre", "correo", "rol", "departamentoId", "activo", "fechaRegistro"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private Map<String, Object> sanitizarPolitica(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "nombre", "categoria", "estado", "requierePago", "version", "fechaCreacion", "descripcion", "activo"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private Map<String, Object> sanitizarDepartamento(Map<String, Object> raw) {
        Map<String, Object> clean = new HashMap<>();
        String[] allowed = {"id", "nombre", "responsableId", "descripcion", "activo"};
        copiarCamposPermitidos(raw, clean, allowed);
        return clean;
    }

    private void copiarCamposPermitidos(Map<String, Object> source, Map<String, Object> target, String[] allowedFields) {
        for (String f : allowedFields) {
            Object val = source.get(f);
            if (val != null) {
                target.put(f, val);
            } else if (f.equals("id") && source.containsKey("_id")) {
                // Mapeo seguro de _id de Mongo a id
                target.put("id", String.valueOf(source.get("_id")));
            }
        }
    }
}
