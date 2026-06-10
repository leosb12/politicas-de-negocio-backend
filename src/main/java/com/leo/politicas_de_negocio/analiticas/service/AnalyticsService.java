package com.leo.politicas_de_negocio.analiticas.service;

import com.leo.politicas_de_negocio.analiticas.client.AnalyticsIaClient;
import com.leo.politicas_de_negocio.analiticas.dto.response.AttentionTimesAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.DashboardSummaryResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.GeneralAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.PolicyImprovementAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskAccumulationAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskRedistributionAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.mapper.AnalyticsMapper;
import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final List<EstadoTarea> ESTADOS_TAREA_ABIERTA = List.of(
            EstadoTarea.PENDIENTE,
            EstadoTarea.EN_PROCESO
    );

    private final PoliticaNegocioRepository politicaRepository;
    private final InstanciaPoliticaRepository instanciaRepository;
    private final TareaActividadRepository tareaRepository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final AnalyticsMapper analyticsMapper;
    private final AnalyticsIaClient analyticsIaClient;

    public GeneralAnalyticsResponse getGeneralMetrics(String adminUserId) {
        assertAdminActivo(adminUserId);

        List<PoliticaNegocio> politicas = politicaRepository.findAll();
        List<InstanciaPolitica> instancias = instanciaRepository.findAll();
        List<TareaActividad> tareas = tareaRepository.findAll();

        DurationAccumulator resolutionAccumulator = new DurationAccumulator();

        long activePolicies = politicas.stream()
                .filter(politica -> politica != null && politica.getEstado() == EstadoPolitica.ACTIVA)
                .count();

        long completedInstances = 0L;
        long rejectedInstances = 0L;
        long inProgressInstances = 0L;
        for (InstanciaPolitica instancia : instancias) {
            if (instancia == null || instancia.getEstadoInstancia() == null) {
                continue;
            }
            if (instancia.getEstadoInstancia() == EstadoInstancia.FINALIZADA) {
                completedInstances++;
                addDurationIfPossible(resolutionAccumulator, instancia.getFechaCreacion(), instancia.getFechaFinalizacion());
            } else if (instancia.getEstadoInstancia() == EstadoInstancia.CANCELADA) {
                rejectedInstances++;
            } else {
                inProgressInstances++;
            }
        }

        long pendingTasks = tareas.stream()
                .filter(this::isOpenTask)
                .count();
        long completedTasks = tareas.stream()
                .filter(tarea -> tarea != null && tarea.getEstadoTarea() == EstadoTarea.COMPLETADA)
                .count();

        return GeneralAnalyticsResponse.builder()
                .totalPolicies(politicas.size())
                .activePolicies(activePolicies)
                .totalInstances(instancias.size())
                .inProgressInstances(inProgressInstances)
                .completedInstances(completedInstances)
                .rejectedInstances(rejectedInstances)
                .pendingTasks(pendingTasks)
                .completedTasks(completedTasks)
                .averageResolutionTimeHours(resolutionAccumulator.hasData() ? round(resolutionAccumulator.averageHours()) : null)
                .hasEnoughResolutionTimeData(resolutionAccumulator.hasData())
                .build();
    }

    public AttentionTimesAnalyticsResponse getAttentionTimes(String adminUserId) {
        assertAdminActivo(adminUserId);

        List<PoliticaNegocio> politicas = politicaRepository.findAll();
        List<InstanciaPolitica> instancias = instanciaRepository.findAll();
        List<TareaActividad> tareas = tareaRepository.findAll();
        Map<String, PoliticaNegocio> politicasPorId = indexPolicies(politicas);
        Map<String, Usuario> usuariosPorId = indexUsers(usuarioRepository.findAll());
        Map<String, Departamento> departamentosPorId = indexDepartments(departamentoRepository.findAll());

        Map<String, DurationAccumulator> promedioPorPolitica = new LinkedHashMap<>();
        Map<String, DurationAccumulator> promedioPorNodo = new LinkedHashMap<>();
        Map<String, DurationAccumulator> promedioPorDepartamento = new LinkedHashMap<>();
        Map<String, DurationAccumulator> promedioPorFuncionario = new LinkedHashMap<>();
        Map<String, NodeDescriptor> nodosPorClave = new HashMap<>();

        for (InstanciaPolitica instancia : instancias) {
            if (instancia == null || instancia.getEstadoInstancia() != EstadoInstancia.FINALIZADA) {
                continue;
            }
            if (canMeasure(instancia.getFechaCreacion(), instancia.getFechaFinalizacion())) {
                double hours = hoursBetween(instancia.getFechaCreacion(), instancia.getFechaFinalizacion());
                promedioPorPolitica.computeIfAbsent(defaultKey(instancia.getPoliticaId()), key -> new DurationAccumulator())
                        .add(hours);
            }
        }

        for (TareaActividad tarea : tareas) {
            if (tarea == null || tarea.getEstadoTarea() != EstadoTarea.COMPLETADA) {
                continue;
            }

            LocalDateTime inicio = preferredTaskStart(tarea);
            LocalDateTime fin = tarea.getFechaFin();
            if (!canMeasure(inicio, fin)) {
                continue;
            }

            double hours = hoursBetween(inicio, fin);
            String nodeKey = nodeKey(tarea);
            promedioPorNodo.computeIfAbsent(nodeKey, key -> new DurationAccumulator()).add(hours);
            nodosPorClave.putIfAbsent(nodeKey, resolveNodeDescriptor(tarea, politicasPorId));

            String departmentId = resolveDepartmentId(tarea, politicasPorId, usuariosPorId);
            if (departmentId != null) {
                promedioPorDepartamento.computeIfAbsent(departmentId, key -> new DurationAccumulator()).add(hours);
            }

            String officialId = normalizedText(tarea.getAsignadoA());
            if (officialId != null) {
                promedioPorFuncionario.computeIfAbsent(officialId, key -> new DurationAccumulator()).add(hours);
            }
        }

        List<AttentionTimesAnalyticsResponse.PolicyAverageResponse> averageByPolicy = promedioPorPolitica.entrySet().stream()
                .filter(entry -> !UNKNOWN_KEY.equals(entry.getKey()))
                .map(entry -> analyticsMapper.toPolicyAverage(
                        entry.getKey(),
                        resolvePolicyName(politicasPorId, entry.getKey()),
                        round(entry.getValue().averageHours()),
                        entry.getValue().count
                ))
                .sorted(Comparator.comparing(AttentionTimesAnalyticsResponse.PolicyAverageResponse::getAverageHours).reversed())
                .toList();

        List<AttentionTimesAnalyticsResponse.NodeAverageResponse> averageByNode = promedioPorNodo.entrySet().stream()
                .map(entry -> {
                    NodeDescriptor descriptor = nodosPorClave.get(entry.getKey());
                    return analyticsMapper.toNodeAverage(
                            descriptor != null ? descriptor.nodeId() : null,
                            descriptor != null ? descriptor.nodeName() : null,
                            round(entry.getValue().averageHours()),
                            entry.getValue().count
                    );
                })
                .sorted(Comparator.comparing(AttentionTimesAnalyticsResponse.NodeAverageResponse::getAverageHours).reversed())
                .toList();

        List<AttentionTimesAnalyticsResponse.DepartmentAverageResponse> averageByDepartment = promedioPorDepartamento.entrySet().stream()
                .map(entry -> analyticsMapper.toDepartmentAverage(
                        entry.getKey(),
                        resolveDepartmentName(departamentosPorId, entry.getKey()),
                        round(entry.getValue().averageHours()),
                        entry.getValue().count
                ))
                .sorted(Comparator.comparing(AttentionTimesAnalyticsResponse.DepartmentAverageResponse::getAverageHours).reversed())
                .toList();

        List<AttentionTimesAnalyticsResponse.OfficialAverageResponse> averageByOfficial = promedioPorFuncionario.entrySet().stream()
                .map(entry -> analyticsMapper.toOfficialAverage(
                        entry.getKey(),
                        resolveUserName(usuariosPorId, entry.getKey()),
                        round(entry.getValue().averageHours()),
                        entry.getValue().count
                ))
                .sorted(Comparator.comparing(AttentionTimesAnalyticsResponse.OfficialAverageResponse::getAverageHours).reversed())
                .toList();

        AttentionTimesAnalyticsResponse.ActivitySpeedResponse slowestActivity = averageByNode.isEmpty()
                ? null
                : analyticsMapper.toActivitySpeed(
                    averageByNode.get(0).getNodeId(),
                    averageByNode.get(0).getNodeName(),
                    averageByNode.get(0).getAverageHours()
                );

        AttentionTimesAnalyticsResponse.ActivitySpeedResponse fastestActivity = averageByNode.isEmpty()
                ? null
                : analyticsMapper.toActivitySpeed(
                    averageByNode.get(averageByNode.size() - 1).getNodeId(),
                    averageByNode.get(averageByNode.size() - 1).getNodeName(),
                    averageByNode.get(averageByNode.size() - 1).getAverageHours()
                );

        boolean hasEnoughData = !averageByPolicy.isEmpty() || !averageByNode.isEmpty()
                || !averageByDepartment.isEmpty() || !averageByOfficial.isEmpty();

        return AttentionTimesAnalyticsResponse.builder()
                .averageByPolicy(averageByPolicy)
                .averageByNode(averageByNode)
                .averageByDepartment(averageByDepartment)
                .averageByOfficial(averageByOfficial)
                .slowestActivity(slowestActivity)
                .fastestActivity(fastestActivity)
                .hasEnoughData(hasEnoughData)
                .build();
    }

    public TaskAccumulationAnalyticsResponse getTaskAccumulation(String adminUserId) {
        assertAdminActivo(adminUserId);

        List<TareaActividad> tareas = tareaRepository.findAll();
        Map<String, PoliticaNegocio> politicasPorId = indexPolicies(politicaRepository.findAll());
        Map<String, Usuario> usuariosPorId = indexUsers(usuarioRepository.findAll());
        Map<String, Departamento> departamentosPorId = indexDepartments(departamentoRepository.findAll());

        Map<String, PendingAccumulator> pendingByOfficial = new LinkedHashMap<>();
        Map<String, PendingAccumulator> pendingByDepartment = new LinkedHashMap<>();
        Map<String, PendingAccumulator> pendingByPolicy = new LinkedHashMap<>();
        Map<String, PendingAccumulator> pendingByNode = new LinkedHashMap<>();
        List<TaskAccumulationAnalyticsResponse.OldestPendingTaskResponse> oldestPendingTasks = new ArrayList<>();

        for (TareaActividad tarea : tareas) {
            if (!isOpenTask(tarea)) {
                continue;
            }

            Long ageHours = calculateTaskAgeHours(tarea);
            String officialId = normalizedText(tarea.getAsignadoA());
            if (officialId != null) {
                pendingByOfficial.computeIfAbsent(officialId, key -> new PendingAccumulator())
                        .add(ageHours);
            }

            String departmentId = resolveDepartmentId(tarea, politicasPorId, usuariosPorId);
            if (departmentId != null) {
                pendingByDepartment.computeIfAbsent(departmentId, key -> new PendingAccumulator())
                        .add(ageHours);
            }

            String policyId = normalizedText(tarea.getPoliticaId());
            if (policyId != null) {
                pendingByPolicy.computeIfAbsent(policyId, key -> new PendingAccumulator())
                        .add(ageHours);
            }

            String nodeKey = nodeKey(tarea);
            pendingByNode.computeIfAbsent(nodeKey, key -> new PendingAccumulator())
                    .add(ageHours);

            oldestPendingTasks.add(TaskAccumulationAnalyticsResponse.OldestPendingTaskResponse.builder()
                    .taskId(tarea.getId())
                    .policyName(resolvePolicyName(politicasPorId, tarea.getPoliticaId()))
                    .nodeName(resolveNodeDescriptor(tarea, politicasPorId).nodeName())
                    .assignedToName(resolveUserName(usuariosPorId, tarea.getAsignadoA()))
                    .departmentName(resolveDepartmentName(departamentosPorId, departmentId))
                    .ageHours(ageHours)
                    .createdAt(tarea.getFechaCreacion())
                    .build());
        }

        List<TaskAccumulationAnalyticsResponse.PendingByOfficialResponse> responseByOfficial = pendingByOfficial.entrySet().stream()
                .map(entry -> analyticsMapper.toPendingByOfficial(
                        entry.getKey(),
                        resolveUserName(usuariosPorId, entry.getKey()),
                        entry.getValue().count,
                        entry.getValue().oldestAgeHours
                ))
                .sorted(Comparator.comparing(TaskAccumulationAnalyticsResponse.PendingByOfficialResponse::getPendingTasks).reversed()
                        .thenComparing(TaskAccumulationAnalyticsResponse.PendingByOfficialResponse::getOldestTaskAgeHours,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TaskAccumulationAnalyticsResponse.PendingByDepartmentResponse> responseByDepartment = pendingByDepartment.entrySet().stream()
                .map(entry -> analyticsMapper.toPendingByDepartment(
                        entry.getKey(),
                        resolveDepartmentName(departamentosPorId, entry.getKey()),
                        entry.getValue().count,
                        entry.getValue().oldestAgeHours
                ))
                .sorted(Comparator.comparing(TaskAccumulationAnalyticsResponse.PendingByDepartmentResponse::getPendingTasks).reversed()
                        .thenComparing(TaskAccumulationAnalyticsResponse.PendingByDepartmentResponse::getOldestTaskAgeHours,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TaskAccumulationAnalyticsResponse.PendingByPolicyResponse> responseByPolicy = pendingByPolicy.entrySet().stream()
                .map(entry -> analyticsMapper.toPendingByPolicy(
                        entry.getKey(),
                        resolvePolicyName(politicasPorId, entry.getKey()),
                        entry.getValue().count,
                        entry.getValue().oldestAgeHours
                ))
                .sorted(Comparator.comparing(TaskAccumulationAnalyticsResponse.PendingByPolicyResponse::getPendingTasks).reversed()
                        .thenComparing(TaskAccumulationAnalyticsResponse.PendingByPolicyResponse::getOldestTaskAgeHours,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TaskAccumulationAnalyticsResponse.PendingByNodeResponse> responseByNode = pendingByNode.entrySet().stream()
                .map(entry -> {
                    String[] parts = splitNodeKey(entry.getKey());
                    return analyticsMapper.toPendingByNode(
                            parts[1],
                            resolveNodeNameFromKey(entry.getKey(), politicasPorId),
                            entry.getValue().count,
                            entry.getValue().oldestAgeHours
                    );
                })
                .sorted(Comparator.comparing(TaskAccumulationAnalyticsResponse.PendingByNodeResponse::getPendingTasks).reversed()
                        .thenComparing(TaskAccumulationAnalyticsResponse.PendingByNodeResponse::getOldestTaskAgeHours,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        List<TaskAccumulationAnalyticsResponse.OldestPendingTaskResponse> oldestPending = oldestPendingTasks.stream()
                .sorted(Comparator.comparing(TaskAccumulationAnalyticsResponse.OldestPendingTaskResponse::getAgeHours,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        return TaskAccumulationAnalyticsResponse.builder()
                .pendingByOfficial(responseByOfficial)
                .pendingByDepartment(responseByDepartment)
                .pendingByPolicy(responseByPolicy)
                .pendingByNode(responseByNode)
                .oldestPendingTasks(oldestPending)
                .build();
    }

    public DashboardSummaryResponse getDashboardSummary(String adminUserId) {
        return DashboardSummaryResponse.builder()
                .general(getGeneralMetrics(adminUserId))
                .attentionTimes(getAttentionTimes(adminUserId))
                .taskAccumulation(getTaskAccumulation(adminUserId))
                .build();
    }

    public BottlenecksAnalyticsResponse getBottlenecks(String adminUserId) {
        DashboardSummaryResponse dashboardSummary = getDashboardSummary(adminUserId);
        return analyticsIaClient.analyzeBottlenecks(dashboardSummary);
    }

    public TaskRedistributionAnalyticsResponse getTaskRedistribution(String adminUserId) {
        DashboardSummaryResponse dashboardSummary = getDashboardSummary(adminUserId);
        return analyticsIaClient.analyzeTaskRedistribution(dashboardSummary);
    }

    public PolicyImprovementAnalyticsResponse getPolicyImprovement(String adminUserId) {
        DashboardSummaryResponse dashboardSummary = getDashboardSummary(adminUserId);
        return analyticsIaClient.analyzePolicyImprovement(dashboardSummary);
    }

    private Usuario assertAdminActivo(String userId) {
        String adminId = normalizedText(userId);
        if (adminId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }

        Usuario actor = usuarioRepository.findByIdAndActivo(adminId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario administrador no autorizado"));

        if (!"ADMIN".equalsIgnoreCase(actor.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Las analiticas globales solo estan disponibles para administradores");
        }
        return actor;
    }

    private Map<String, PoliticaNegocio> indexPolicies(List<PoliticaNegocio> politicas) {
        Map<String, PoliticaNegocio> index = new HashMap<>();
        for (PoliticaNegocio politica : politicas) {
            if (politica != null && normalizedText(politica.getId()) != null) {
                index.put(politica.getId(), politica);
            }
        }
        return index;
    }

    private Map<String, Usuario> indexUsers(List<Usuario> usuarios) {
        Map<String, Usuario> index = new HashMap<>();
        for (Usuario usuario : usuarios) {
            if (usuario != null && normalizedText(usuario.getId()) != null) {
                index.put(usuario.getId(), usuario);
            }
        }
        return index;
    }

    private Map<String, Departamento> indexDepartments(List<Departamento> departamentos) {
        Map<String, Departamento> index = new HashMap<>();
        for (Departamento departamento : departamentos) {
            if (departamento != null && normalizedText(departamento.getId()) != null) {
                index.put(departamento.getId(), departamento);
            }
        }
        return index;
    }

    private void addDurationIfPossible(DurationAccumulator accumulator, LocalDateTime start, LocalDateTime end) {
        if (canMeasure(start, end)) {
            accumulator.add(hoursBetween(start, end));
        }
    }

    private boolean canMeasure(LocalDateTime start, LocalDateTime end) {
        return start != null && end != null && !end.isBefore(start);
    }

    private double hoursBetween(LocalDateTime start, LocalDateTime end) {
        return Duration.between(start, end).toMinutes() / 60.0d;
    }

    private LocalDateTime preferredTaskStart(TareaActividad tarea) {
        return tarea.getFechaInicio() != null ? tarea.getFechaInicio() : tarea.getFechaCreacion();
    }

    private boolean isOpenTask(TareaActividad tarea) {
        return tarea != null && ESTADOS_TAREA_ABIERTA.contains(tarea.getEstadoTarea());
    }

    private Long calculateTaskAgeHours(TareaActividad tarea) {
        LocalDateTime reference = preferredTaskStart(tarea);
        if (reference == null) {
            return null;
        }
        return Math.max(0L, Duration.between(reference, LocalDateTime.now()).toHours());
    }

    private String resolveDepartmentId(
            TareaActividad tarea,
            Map<String, PoliticaNegocio> politicasPorId,
            Map<String, Usuario> usuariosPorId
    ) {
        String responsableTipo = normalizedText(tarea.getResponsableTipo());
        if ("DEPARTAMENTO".equalsIgnoreCase(responsableTipo)) {
            return normalizedText(tarea.getResponsableId());
        }

        String assignedUserId = normalizedText(tarea.getAsignadoA());
        if (assignedUserId != null) {
            Usuario assignedUser = usuariosPorId.get(assignedUserId);
            String assignedDepartmentId = normalizedText(assignedUser != null ? assignedUser.getDepartamentoId() : null);
            if (assignedDepartmentId != null) {
                return assignedDepartmentId;
            }
        }

        if ("USUARIO".equalsIgnoreCase(responsableTipo)) {
            Usuario responsibleUser = usuariosPorId.get(normalizedText(tarea.getResponsableId()));
            String responsibleDepartmentId = normalizedText(responsibleUser != null ? responsibleUser.getDepartamentoId() : null);
            if (responsibleDepartmentId != null) {
                return responsibleDepartmentId;
            }
        }

        PoliticaNegocio politica = politicasPorId.get(normalizedText(tarea.getPoliticaId()));
        if (politica == null || politica.getNodos() == null) {
            return null;
        }
        String nodeId = normalizedText(tarea.getNodoId());
        if (nodeId != null) {
            for (Nodo nodo : politica.getNodos()) {
                if (nodo != null && nodeId.equals(normalizedText(nodo.getId()))) {
                    return normalizedText(nodo.getDepartamentoId());
                }
            }
        }
        return null;
    }

    private NodeDescriptor resolveNodeDescriptor(TareaActividad tarea, Map<String, PoliticaNegocio> politicasPorId) {
        String nodeId = normalizedText(tarea.getNodoId());
        String nodeName = normalizedText(tarea.getNombreNodo());

        PoliticaNegocio politica = politicasPorId.get(normalizedText(tarea.getPoliticaId()));
        return new NodeDescriptor(nodeId, nodeName);
    }

    private String resolvePolicyName(Map<String, PoliticaNegocio> politicasPorId, String policyId) {
        PoliticaNegocio politica = politicasPorId.get(normalizedText(policyId));
        return politica != null ? normalizedText(politica.getNombre()) : null;
    }

    private String resolveUserName(Map<String, Usuario> usuariosPorId, String userId) {
        Usuario usuario = usuariosPorId.get(normalizedText(userId));
        return usuario != null ? normalizedText(usuario.getNombre()) : null;
    }

    private String resolveDepartmentName(Map<String, Departamento> departamentosPorId, String departmentId) {
        Departamento departamento = departamentosPorId.get(normalizedText(departmentId));
        return departamento != null ? normalizedText(departamento.getNombre()) : null;
    }

    private String nodeKey(TareaActividad tarea) {
        return defaultKey(tarea.getPoliticaId()) + "::" + defaultKey(tarea.getNodoId());
    }

    private String resolveNodeNameFromKey(String nodeKey, Map<String, PoliticaNegocio> politicasPorId) {
        String[] parts = splitNodeKey(nodeKey);
        String policyId = parts[0];
        String nodeId = parts[1];
        PoliticaNegocio politica = politicasPorId.get(policyId);
        if (politica == null || politica.getNodos() == null || nodeId == null) {
            return null;
        }
        List<Nodo> nodos = politica.getNodos();
        for (int i = 0; i < nodos.size(); i++) {
            Nodo nodo = nodos.get(i);
            if (nodo != null && nodeId.equals(normalizedText(nodo.getId()))) {
                return normalizedText(nodo.getNombre());
            }
        }
        return null;
    }

    private String[] splitNodeKey(String nodeKey) {
        String[] parts = nodeKey.split("::", 2);
        String policyId = parts.length > 0 && !UNKNOWN_KEY.equals(parts[0]) ? parts[0] : null;
        String nodeId = parts.length > 1 && !UNKNOWN_KEY.equals(parts[1]) ? parts[1] : null;
        return new String[]{policyId, nodeId};
    }

    private String defaultKey(String value) {
        String normalized = normalizedText(value);
        return normalized != null ? normalized : UNKNOWN_KEY;
    }

    private Double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String normalizedText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static final String UNKNOWN_KEY = "__UNKNOWN__";

    private static final class DurationAccumulator {
        private long count;
        private double totalHours;

        private void add(double hours) {
            this.count++;
            this.totalHours += hours;
        }

        private boolean hasData() {
            return count > 0;
        }

        private double averageHours() {
            return count == 0 ? 0.0d : totalHours / count;
        }
    }

    private static final class PendingAccumulator {
        private long count;
        private Long oldestAgeHours;

        private void add(Long ageHours) {
            this.count++;
            if (ageHours == null) {
                return;
            }
            if (oldestAgeHours == null || ageHours > oldestAgeHours) {
                oldestAgeHours = ageHours;
            }
        }
    }

    private record NodeDescriptor(String nodeId, String nodeName) {
    }
}
