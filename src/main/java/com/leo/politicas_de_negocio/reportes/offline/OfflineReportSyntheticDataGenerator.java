package com.leo.politicas_de_negocio.reportes.offline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class OfflineReportSyntheticDataGenerator {

    @Value("${offline.reports.simulation.seed:12345}")
    private long seed;

    @Value("${offline.reports.simulation.min-tramites:80}")
    private int minTramites;

    @Value("${offline.reports.simulation.max-tramites:300}")
    private int maxTramites;

    @Value("${offline.reports.simulation.months-back:6}")
    private int monthsBack;

    @Value("${offline.reports.simulation.use-real-catalogs:true}")
    private boolean useRealCatalogs;

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateSimulationData(Map<String, Object> snapshot, int existingRealCount) {
        log.info("OfflineReportSyntheticDataGenerator: Generando datos sintéticos. Seed: {}, Min: {}, Max: {}, MonthsBack: {}", 
                seed, minTramites, maxTramites, monthsBack);
        Random rand = new Random(seed);

        // Número total de trámites objetivo a simular
        int targetCount = minTramites + rand.nextInt(maxTramites - minTramites + 1);
        int needToGenerate = targetCount - existingRealCount;
        if (needToGenerate <= 0) {
            log.info("No se requieren generar trámites sintéticos. El conteo real ya es {}", existingRealCount);
            return snapshot;
        }

        log.info("Generando {} instancias de trámites de manera sintética", needToGenerate);

        // Obtener catálogos del snapshot
        List<Map<String, Object>> realUsers = (List<Map<String, Object>>) snapshot.get("usuarios");
        List<Map<String, Object>> realPolicies = (List<Map<String, Object>>) snapshot.get("politicas_negocio");
        List<Map<String, Object>> realDepts = (List<Map<String, Object>>) snapshot.get("departamentos");

        if (realUsers == null) realUsers = new ArrayList<>();
        if (realPolicies == null) realPolicies = new ArrayList<>();
        if (realDepts == null) realDepts = new ArrayList<>();

        List<Map<String, Object>> users = new ArrayList<>(realUsers);
        List<Map<String, Object>> policies = new ArrayList<>(realPolicies);
        List<Map<String, Object>> depts = new ArrayList<>(realDepts);

        // Fallbacks si no existen datos en el snapshot
        if (users.isEmpty()) {
            log.info("El catálogo de usuarios está vacío. Generando fallback sintético.");
            String[] roles = {"ADMINISTRADOR", "FUNCIONARIO", "CLIENTE"};
            for (int i = 1; i <= 10; i++) {
                Map<String, Object> u = new HashMap<>();
                u.put("id", "u_sim_" + i);
                u.put("nombre", "Funcionario Sim " + i);
                u.put("correo", "func" + i + "@sim.com");
                u.put("rol", roles[rand.nextInt(roles.length)]);
                u.put("departamentoId", "d_sim_" + (rand.nextInt(3) + 1));
                users.add(u);
            }
        }
        if (policies.isEmpty()) {
            log.info("El catálogo de políticas está vacío. Generando fallback sintético.");
            for (int i = 1; i <= 5; i++) {
                Map<String, Object> p = new HashMap<>();
                p.put("id", "p_sim_" + i);
                p.put("nombre", "Política Sim " + i);
                p.put("categoria", "Seguridad");
                p.put("requierePago", false);
                policies.add(p);
            }
        }
        if (depts.isEmpty()) {
            log.info("El catálogo de departamentos está vacío. Generando fallback sintético.");
            for (int i = 1; i <= 3; i++) {
                Map<String, Object> d = new HashMap<>();
                d.put("id", "d_sim_" + i);
                d.put("nombre", "Departamento Sim " + i);
                depts.add(d);
            }
        }

        // Agrupar usuarios por rol para asignación coherente
        List<Map<String, Object>> clients = new ArrayList<>();
        List<Map<String, Object>> funcionarios = new ArrayList<>();
        for (Map<String, Object> u : users) {
            String rol = String.valueOf(u.get("rol"));
            if ("FUNCIONARIO".equalsIgnoreCase(rol)) {
                funcionarios.add(u);
            } else {
                clients.add(u);
            }
        }
        if (funcionarios.isEmpty()) funcionarios = users;
        if (clients.isEmpty()) clients = users;

        List<Map<String, Object>> syntheticInstances = new ArrayList<>();
        List<Map<String, Object>> syntheticTasks = new ArrayList<>();

        // Distribución de estados: FINALIZADA 45%, EN_CURSO 30%, PENDIENTE 10%, RECHAZADO 10%, CANCELADA 5%
        String[] states = {"FINALIZADA", "EN_CURSO", "PENDIENTE", "RECHAZADO", "CANCELADA"};
        int[] distribution = {45, 30, 10, 10, 5};

        LocalDateTime now = LocalDateTime.now();

        for (int i = 0; i < needToGenerate; i++) {
            Map<String, Object> inst = new HashMap<>();
            String instId = "inst_sim_" + (existingRealCount + i + 1);
            inst.put("id", instId);
            inst.put("codigoTramite", "TRAM-SIM-" + String.format("%04d", existingRealCount + i + 1));

            // Seleccionar estado basado en la distribución
            int roll = rand.nextInt(100);
            String state = "PENDIENTE";
            int cum = 0;
            for (int s = 0; s < states.length; s++) {
                cum += distribution[s];
                if (roll < cum) {
                    state = states[s];
                    break;
                }
            }
            inst.put("estadoInstancia", state);

            // Generar fechas deterministas entre 2026-04-01 y 2026-06-30 (91 días de rango)
            int randomDays = rand.nextInt(91);
            LocalDateTime creacion = LocalDateTime.of(2026, 4, 1, 0, 0, 0)
                    .plusDays(randomDays)
                    .plusHours(rand.nextInt(24))
                    .plusMinutes(rand.nextInt(60));
            inst.put("fechaCreacion", creacion.toString());

            if ("FINALIZADA".equals(state) || "RECHAZADO".equals(state) || "CANCELADA".equals(state)) {
                LocalDateTime finalizacion = creacion.plusDays(rand.nextInt(15) + 1).plusHours(rand.nextInt(24));
                LocalDateTime maxLimit = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
                if (finalizacion.isAfter(maxLimit)) {
                    finalizacion = maxLimit;
                }
                inst.put("fechaFinalizacion", finalizacion.toString());
            }

            // Asignar recursos reales de catálogos
            Map<String, Object> policy = policies.get(rand.nextInt(policies.size()));
            inst.put("politicaId", policy.get("id"));
            inst.put("politicaNombre", policy.get("nombre"));

            Map<String, Object> dept = depts.get(rand.nextInt(depts.size()));
            inst.put("departamentoId", dept.get("id"));
            inst.put("departamentoActual", dept.get("nombre"));

            Map<String, Object> client = clients.get(rand.nextInt(clients.size()));
            inst.put("creadaPor", client.get("id"));

            Map<String, Object> func = funcionarios.get(rand.nextInt(funcionarios.size()));
            inst.put("funcionarioAsignado", func.get("id"));

            inst.put("requierePago", false);
            inst.put("prioridad", rand.nextInt(3) == 0 ? "ALTA" : "MEDIA");

            syntheticInstances.add(inst);

            // Generar tareas para mantener consistencia con los trámites
            int taskCount = rand.nextInt(3) + 1;
            for (int t = 0; t < taskCount; t++) {
                Map<String, Object> task = new HashMap<>();
                task.put("id", "task_sim_" + instId + "_" + t);
                task.put("instanciaId", instId);
                task.put("responsableId", func.get("id"));

                String taskState = "PENDIENTE";
                if ("FINALIZADA".equals(state)) {
                    taskState = "COMPLETADA";
                } else if ("EN_CURSO".equals(state) || "RECHAZADO".equals(state) || "CANCELADA".equals(state)) {
                    taskState = rand.nextBoolean() ? "COMPLETADA" : "PENDIENTE";
                }
                task.put("estado", taskState);
                task.put("fechaCreacion", creacion.toString());

                LocalDateTime limite = creacion.plusDays(rand.nextInt(5) + 2);
                LocalDateTime maxLimit = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
                if (limite.isAfter(maxLimit)) limite = maxLimit;
                task.put("fechaLimite", limite.toString());

                if ("COMPLETADA".equals(taskState)) {
                    LocalDateTime completado = creacion.plusDays(rand.nextInt(5) + 1);
                    if (completado.isAfter(maxLimit)) completado = maxLimit;
                    task.put("fechaCompletado", completado.toString());
                }

                String[] actNames = {"Revisión de documentos", "Validación de requisitos", "Aprobación de jefatura", "Firma digital"};
                task.put("actividadNombre", actNames[t % actNames.length]);
                task.put("tipo", "OPERATIVA");

                syntheticTasks.add(task);
            }
        }

        // Combinar datos reales y simulados
        List<Map<String, Object>> existingInstances = (List<Map<String, Object>>) snapshot.get("instancias_politica");
        if (existingInstances == null) {
            existingInstances = new ArrayList<>();
        } else {
            existingInstances = new ArrayList<>(existingInstances);
        }
        existingInstances.addAll(syntheticInstances);

        List<Map<String, Object>> existingTasks = (List<Map<String, Object>>) snapshot.get("tareas_actividad");
        if (existingTasks == null) {
            existingTasks = new ArrayList<>();
        } else {
            existingTasks = new ArrayList<>(existingTasks);
        }
        existingTasks.addAll(syntheticTasks);

        // Crear snapshot mezclado sin mutar el original
        Map<String, Object> mergedSnapshot = new HashMap<>(snapshot);
        mergedSnapshot.put("instancias_politica", existingInstances);
        mergedSnapshot.put("tareas_actividad", existingTasks);

        // Eliminar pagos simulados si es que hay en el snapshot
        mergedSnapshot.remove("pagos");

        // Actualizar metadatos
        Map<String, Object> metadata = (Map<String, Object>) mergedSnapshot.get("metadata");
        if (metadata != null) {
            Map<String, Object> newMetadata = new HashMap<>(metadata);
            Map<String, Integer> counts = (Map<String, Integer>) newMetadata.get("counts");
            if (counts != null) {
                Map<String, Integer> newCounts = new HashMap<>(counts);
                newCounts.put("instancias_politica", existingInstances.size());
                newCounts.put("tareas_actividad", existingTasks.size());
                newCounts.put("pagos", 0);
                newMetadata.put("counts", newCounts);
            }
            newMetadata.put("mode", existingRealCount > 0 ? "OFFLINE_PARTIAL_DATA_DEEP_LEARNING_LOCAL" : "OFFLINE_SIMULATED_FALLBACK");
            mergedSnapshot.put("metadata", newMetadata);
        }

        log.info("Simulación finalizada. Total instancias en memoria: {}, Total tareas: {}", existingInstances.size(), existingTasks.size());
        return mergedSnapshot;
    }
}
