package com.leo.politicas_de_negocio.simulation.service;

import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.simulation.client.SimulationAiInsightResponse;
import com.leo.politicas_de_negocio.simulation.client.SimulationIaClient;
import com.leo.politicas_de_negocio.simulation.dto.PolicyComparisonResponse;
import com.leo.politicas_de_negocio.simulation.dto.SimulationComparisonRequest;
import com.leo.politicas_de_negocio.simulation.dto.SimulationRunRequest;
import com.leo.politicas_de_negocio.simulation.dto.SimulationRunResponse;
import com.leo.politicas_de_negocio.simulation.model.DecisionSimulationStats;
import com.leo.politicas_de_negocio.simulation.model.NodeSimulationStats;
import com.leo.politicas_de_negocio.simulation.model.PolicyComparisonResult;
import com.leo.politicas_de_negocio.simulation.model.SimulationResult;
import com.leo.politicas_de_negocio.simulation.model.SimulationRun;
import com.leo.politicas_de_negocio.simulation.repository.SimulationRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final int DEFAULT_INSTANCES = 100;
    private static final int MAX_INSTANCES = 10000;
    private static final double DEFAULT_BASE_NODE_DURATION_HOURS = 2.0d;
    private static final double DEFAULT_VARIABILITY_PERCENT = 20.0d;
    private static final double BOTTLENECK_THRESHOLD_RATIO = 0.75d;

    private final SimulationRepository simulationRepository;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final SimulationIaClient simulationIaClient;

    public SimulationRunResponse runSimulation(String adminUserId, String policyId, SimulationRunRequest request) {
        Usuario actor = assertAdminActivo(adminUserId);
        SimulationConfiguration config = normalizeRunRequest(request);
        PoliticaNegocio policy = getPolicyOrThrow(policyId);
        GraphContext graph = buildGraph(policy);

        SimulationResult result = simulatePolicy(policy, graph, config, actor.getId());
        SimulationRun run = simulationRepository.save(SimulationRun.builder()
                .policyId(policy.getId())
                .policyName(policy.getNombre())
                .instances(config.instances())
                .baseNodeDurationHours(config.baseNodeDurationHours())
                .variabilityPercent(config.variabilityPercent())
                .includeAiAnalysis(config.includeAiAnalysis())
                .randomSeed(config.randomSeed())
                .createdBy(actor.getId())
                .createdAt(LocalDateTime.now())
                .result(result)
                .build());

        return toResponse(run);
    }

    public SimulationRunResponse getSimulationById(String adminUserId, String simulationId) {
        assertAdminActivo(adminUserId);
        SimulationRun run = simulationRepository.findById(normalizeRequired(simulationId, "El simulationId es obligatorio"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Simulacion no encontrada con ID: " + simulationId));
        return toResponse(run);
    }

    public List<SimulationRunResponse> getSimulationsByPolicy(String adminUserId, String policyId) {
        assertAdminActivo(adminUserId);
        PoliticaNegocio policy = getPolicyOrThrow(policyId);
        return simulationRepository.findByPolicyIdOrderByCreatedAtDesc(policy.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public PolicyComparisonResponse comparePolicies(String adminUserId, SimulationComparisonRequest request) {
        Usuario actor = assertAdminActivo(adminUserId);
        ComparisonConfiguration config = normalizeComparisonRequest(request);

        if (config.firstPolicyId().equals(config.secondPolicyId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar dos politicas distintas para comparar");
        }

        PoliticaNegocio firstPolicy = getPolicyOrThrow(config.firstPolicyId());
        PoliticaNegocio secondPolicy = getPolicyOrThrow(config.secondPolicyId());
        GraphContext firstGraph = buildGraph(firstPolicy);
        GraphContext secondGraph = buildGraph(secondPolicy);

        SimulationConfiguration sharedConfig = new SimulationConfiguration(
                config.instances(),
                config.baseNodeDurationHours(),
                config.variabilityPercent(),
                config.includeAiAnalysis(),
                config.randomSeed()
        );

        SimulationResult firstResult = simulatePolicy(firstPolicy, firstGraph, sharedConfig.withAi(false), actor.getId());
        SimulationResult secondResult = simulatePolicy(secondPolicy, secondGraph, sharedConfig.withAi(false), actor.getId());

        PolicyComparisonResult result = buildComparisonResult(firstPolicy, secondPolicy, firstResult, secondResult, config);
        if (config.includeAiAnalysis()) {
            SimulationAiInsightResponse aiResponse = simulationIaClient.comparePolicies(Map.of(
                    "firstPolicy", firstPolicy,
                    "secondPolicy", secondPolicy,
                    "configuration", config,
                    "comparison", result
            ));
            result.setAiSummary(aiResponse.getSummary());
            result.setAiSource(aiResponse.getSource());
            result.setAiAvailable(aiResponse.isAvailable());
        } else {
            result.setAiSummary(null);
            result.setAiSource(null);
            result.setAiAvailable(false);
        }

        return PolicyComparisonResponse.builder()
                .result(result)
                .build();
    }

    private SimulationResult simulatePolicy(
            PoliticaNegocio policy,
            GraphContext graph,
            SimulationConfiguration config,
            String actorId
    ) {
        Map<String, NodeAccumulator> nodeAccumulators = new LinkedHashMap<>();
        Map<String, DecisionAccumulator> decisionAccumulators = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        long seed = config.randomSeed() != null ? config.randomSeed() : System.nanoTime();
        Random random = new Random(seed);
        int maxStepsPerInstance = Math.max(20, graph.nodesById().size() * 10);
        double totalEstimatedTimeHours = 0.0d;

        for (int i = 0; i < config.instances(); i++) {
            Deque<String> queue = new ArrayDeque<>();
            queue.add(graph.startNodeId());
            int processedSteps = 0;

            while (!queue.isEmpty()) {
                String currentNodeId = queue.poll();
                Nodo node = graph.nodesById().get(currentNodeId);
                if (node == null) {
                    continue;
                }

                processedSteps++;
                if (processedSteps > maxStepsPerInstance) {
                    warnings.add("La politica " + safeText(policy.getNombre(), policy.getId())
                            + " tiene ciclos o demasiadas transiciones; se corto una instancia para evitar un loop infinito.");
                    break;
                }

                double nodeDuration = estimateNodeDuration(node, config.baseNodeDurationHours(), config.variabilityPercent(), random);
                totalEstimatedTimeHours += nodeDuration;

                nodeAccumulators.computeIfAbsent(
                        node.getId(),
                        ignored -> new NodeAccumulator(node)
                ).add(nodeDuration);

                List<String> nextNodes = graph.transitionsByOrigin().getOrDefault(node.getId(), List.of());
                if (nextNodes.isEmpty()) {
                    continue;
                }

                if (node.getTipo() == TipoNodo.DECISION) {
                    int selectedIndex = random.nextInt(nextNodes.size());
                    String selectedNodeId = nextNodes.get(selectedIndex);
                    decisionAccumulators.computeIfAbsent(node.getId(), ignored -> new DecisionAccumulator(node))
                            .recordOutcome(selectedNodeId);
                    queue.add(selectedNodeId);
                    continue;
                }

                if (node.getTipo() == TipoNodo.FORK) {
                    queue.addAll(nextNodes);
                    continue;
                }

                queue.add(nextNodes.get(0));
            }
        }

        List<NodeSimulationStats> nodeStats = buildNodeStats(nodeAccumulators);
        List<DecisionSimulationStats> decisionStats = buildDecisionStats(decisionAccumulators, graph.nodesById());
        List<NodeSimulationStats> bottlenecks = detectBottlenecks(nodeStats);
        NodeSimulationStats highestLoadNode = nodeStats.isEmpty() ? null : nodeStats.get(0);

        SimulationResult result = SimulationResult.builder()
                .instancesSimulated(config.instances())
                .totalEstimatedTimeHours(round(totalEstimatedTimeHours))
                .averageEstimatedTimeHours(round(totalEstimatedTimeHours / config.instances()))
                .highestLoadNodeId(highestLoadNode != null ? highestLoadNode.getNodeId() : null)
                .highestLoadNodeName(highestLoadNode != null ? highestLoadNode.getNodeName() : null)
                .highestLoadPercentage(highestLoadNode != null ? highestLoadNode.getLoadPercentage() : 0.0d)
                .bottleneckNodeIds(bottlenecks.stream().map(NodeSimulationStats::getNodeId).toList())
                .bottleneckNodeNames(bottlenecks.stream().map(NodeSimulationStats::getNodeName).toList())
                .nodeStats(nodeStats)
                .decisionStats(decisionStats)
                .warnings(warnings.stream().distinct().toList())
                .generatedAt(LocalDateTime.now())
                .aiAvailable(false)
                .build();

        if (config.includeAiAnalysis()) {
            SimulationAiInsightResponse aiResponse = simulationIaClient.analyzeSimulation(Map.of(
                    "policy", policy,
                    "configuration", config,
                    "result", result,
                    "actorId", actorId
            ));
            result.setAiSummary(aiResponse.getSummary());
            result.setAiSource(aiResponse.getSource());
            result.setAiAvailable(aiResponse.isAvailable());
        }

        return result;
    }

    private PolicyComparisonResult buildComparisonResult(
            PoliticaNegocio firstPolicy,
            PoliticaNegocio secondPolicy,
            SimulationResult firstResult,
            SimulationResult secondResult,
            ComparisonConfiguration config
    ) {
        double firstAverage = firstResult.getAverageEstimatedTimeHours();
        double secondAverage = secondResult.getAverageEstimatedTimeHours();
        boolean firstIsBetter = firstAverage <= secondAverage;
        String moreEfficientPolicyId = firstIsBetter ? firstPolicy.getId() : secondPolicy.getId();
        String moreEfficientPolicyName = firstIsBetter ? firstPolicy.getNombre() : secondPolicy.getNombre();
        double difference = Math.abs(firstAverage - secondAverage);

        List<String> highlights = new ArrayList<>();
        highlights.add("Ambas politicas se simularon con " + config.instances() + " instancias.");
        highlights.add("La diferencia promedio estimada es de " + round(difference) + " horas.");
        highlights.add("La politica mas eficiente por tiempo estimado es " + safeText(moreEfficientPolicyName, moreEfficientPolicyId) + ".");

        long firstBottlenecks = firstResult.getBottleneckNodeIds() != null ? firstResult.getBottleneckNodeIds().size() : 0L;
        long secondBottlenecks = secondResult.getBottleneckNodeIds() != null ? secondResult.getBottleneckNodeIds().size() : 0L;
        if (firstBottlenecks != secondBottlenecks) {
            String lessBottlenecksPolicyName = firstBottlenecks <= secondBottlenecks ? firstPolicy.getNombre() : secondPolicy.getNombre();
            highlights.add("La politica con menos cuellos de botella detectados es " + safeText(lessBottlenecksPolicyName, null) + ".");
        }

        return PolicyComparisonResult.builder()
                .firstPolicyId(firstPolicy.getId())
                .firstPolicyName(firstPolicy.getNombre())
                .secondPolicyId(secondPolicy.getId())
                .secondPolicyName(secondPolicy.getNombre())
                .firstAverageEstimatedTimeHours(firstAverage)
                .secondAverageEstimatedTimeHours(secondAverage)
                .firstBottleneckCount(firstBottlenecks)
                .secondBottleneckCount(secondBottlenecks)
                .averageTimeDifferenceHours(round(difference))
                .moreEfficientPolicyId(moreEfficientPolicyId)
                .moreEfficientPolicyName(moreEfficientPolicyName)
                .conclusion(buildConclusion(firstPolicy, secondPolicy, firstAverage, secondAverage, firstBottlenecks, secondBottlenecks))
                .comparisonHighlights(highlights)
                .firstPolicyResult(firstResult)
                .secondPolicyResult(secondResult)
                .comparedAt(LocalDateTime.now())
                .build();
    }

    private String buildConclusion(
            PoliticaNegocio firstPolicy,
            PoliticaNegocio secondPolicy,
            double firstAverage,
            double secondAverage,
            long firstBottlenecks,
            long secondBottlenecks
    ) {
        String firstName = safeText(firstPolicy.getNombre(), firstPolicy.getId());
        String secondName = safeText(secondPolicy.getNombre(), secondPolicy.getId());

        if (firstAverage < secondAverage && firstBottlenecks <= secondBottlenecks) {
            return firstName + " parece mas eficiente por menor tiempo promedio y menor o igual cantidad de cuellos de botella.";
        }
        if (secondAverage < firstAverage && secondBottlenecks <= firstBottlenecks) {
            return secondName + " parece mas eficiente por menor tiempo promedio y menor o igual cantidad de cuellos de botella.";
        }
        if (firstAverage == secondAverage) {
            return "Ambas politicas presentan un tiempo promedio estimado equivalente; conviene revisar los cuellos de botella y el analisis IA.";
        }
        return "La comparacion muestra trade-offs entre tiempo promedio y carga por nodo; la politica recomendada prioriza el menor tiempo estimado.";
    }

    private List<NodeSimulationStats> buildNodeStats(Map<String, NodeAccumulator> nodeAccumulators) {
        long totalExecutions = nodeAccumulators.values().stream()
                .mapToLong(NodeAccumulator::executions)
                .sum();

        List<NodeSimulationStats> stats = nodeAccumulators.values().stream()
                .map(accumulator -> NodeSimulationStats.builder()
                        .nodeId(accumulator.node().getId())
                        .nodeName(accumulator.node().getNombre())
                        .nodeType(accumulator.node().getTipo() != null ? accumulator.node().getTipo().name() : null)
                        .executions(accumulator.executions())
                        .totalEstimatedTimeHours(round(accumulator.totalEstimatedTimeHours()))
                        .averageEstimatedTimeHours(round(accumulator.averageEstimatedTimeHours()))
                        .loadPercentage(totalExecutions == 0 ? 0.0d : round((accumulator.executions() * 100.0d) / totalExecutions))
                        .bottleneck(false)
                        .build())
                .sorted(Comparator.comparing(NodeSimulationStats::getLoadPercentage).reversed()
                        .thenComparing(NodeSimulationStats::getAverageEstimatedTimeHours, Comparator.reverseOrder()))
                .toList();

        List<NodeSimulationStats> bottlenecks = detectBottlenecks(stats);
        Map<String, Boolean> bottleneckByNodeId = new HashMap<>();
        for (NodeSimulationStats stat : bottlenecks) {
            bottleneckByNodeId.put(stat.getNodeId(), true);
        }
        stats.forEach(stat -> stat.setBottleneck(Boolean.TRUE.equals(bottleneckByNodeId.get(stat.getNodeId()))));
        return stats;
    }

    private List<NodeSimulationStats> detectBottlenecks(List<NodeSimulationStats> stats) {
        if (stats.isEmpty()) {
            return List.of();
        }
        double maxScore = stats.stream()
                .mapToDouble(stat -> stat.getLoadPercentage() * Math.max(1.0d, stat.getAverageEstimatedTimeHours()))
                .max()
                .orElse(0.0d);
        if (maxScore <= 0.0d) {
            return List.of();
        }

        return stats.stream()
                .filter(stat -> {
                    double score = stat.getLoadPercentage() * Math.max(1.0d, stat.getAverageEstimatedTimeHours());
                    return score >= maxScore * BOTTLENECK_THRESHOLD_RATIO;
                })
                .limit(3)
                .toList();
    }

    private List<DecisionSimulationStats> buildDecisionStats(
            Map<String, DecisionAccumulator> decisionAccumulators,
            Map<String, Nodo> nodesById
    ) {
        return decisionAccumulators.values().stream()
                .map(accumulator -> {
                    Map<String, Long> outcomes = new LinkedHashMap<>();
                    accumulator.outcomes().forEach((nodeId, count) -> {
                        Nodo targetNode = nodesById.get(nodeId);
                        String label = targetNode != null && normalize(targetNode.getNombre()) != null
                                ? targetNode.getNombre() + " (" + nodeId + ")"
                                : nodeId;
                        outcomes.put(label, count);
                    });
                    return DecisionSimulationStats.builder()
                            .nodeId(accumulator.node().getId())
                            .nodeName(accumulator.node().getNombre())
                            .totalDecisions(accumulator.totalDecisions())
                            .outcomes(outcomes)
                            .build();
                })
                .sorted(Comparator.comparing(DecisionSimulationStats::getTotalDecisions).reversed())
                .toList();
    }

    private double estimateNodeDuration(
            Nodo node,
            double baseNodeDurationHours,
            double variabilityPercent,
            Random random
    ) {
        double typeFactor = switch (node.getTipo() != null ? node.getTipo() : TipoNodo.ACTIVIDAD) {
            case INICIO, FIN -> 0.10d;
            case DECISION -> 0.35d;
            case FORK, JOIN -> 0.20d;
            case ACTIVIDAD -> 1.0d;
        };

        double complexityFactor = 1.0d;
        if (node.getFormulario() != null) {
            complexityFactor += Math.min(1.0d, node.getFormulario().size() * 0.12d);
        }
        if (node.getCondiciones() != null) {
            complexityFactor += Math.min(0.8d, node.getCondiciones().size() * 0.15d);
        }
        if (normalize(node.getResponsableTipo()) != null) {
            complexityFactor += 0.10d;
        }
        if (normalize(node.getDepartamentoId()) != null) {
            complexityFactor += 0.05d;
        }

        double variabilityRange = Math.max(0.0d, variabilityPercent) / 100.0d;
        double randomFactor = 1.0d + ((random.nextDouble() * 2.0d) - 1.0d) * variabilityRange;
        double estimated = baseNodeDurationHours * typeFactor * complexityFactor * randomFactor;
        return round(Math.max(0.05d, estimated));
    }

    private GraphContext buildGraph(PoliticaNegocio policy) {
        List<Nodo> nodes = Optional.ofNullable(policy.getNodos()).orElse(List.of());
        if (nodes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La politica no tiene nodos para simular");
        }

        Map<String, Nodo> nodesById = new LinkedHashMap<>();
        for (Nodo node : nodes) {
            if (node == null || normalize(node.getId()) == null) {
                continue;
            }
            nodesById.put(node.getId(), node);
        }

        if (nodesById.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La politica no tiene nodos validos para simular");
        }

        String startNodeId = nodesById.values().stream()
                .filter(node -> node.getTipo() == TipoNodo.INICIO)
                .map(Nodo::getId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "La politica no tiene nodo de INICIO para simular"));

        Map<String, List<String>> transitionsByOrigin = new LinkedHashMap<>();
        for (Conexion connection : Optional.ofNullable(policy.getConexiones()).orElse(List.of())) {
            if (connection == null) {
                continue;
            }
            String origin = normalize(connection.getOrigen());
            String target = normalize(connection.getDestino());
            if (origin == null || target == null) {
                continue;
            }
            if (!nodesById.containsKey(origin) || !nodesById.containsKey(target)) {
                continue;
            }
            transitionsByOrigin.computeIfAbsent(origin, ignored -> new ArrayList<>()).add(target);
        }

        return new GraphContext(nodesById, transitionsByOrigin, startNodeId);
    }

    private Usuario assertAdminActivo(String userId) {
        String adminId = normalizeRequired(userId, "Debe enviar el header X-Admin-User-Id");
        Usuario actor = usuarioRepository.findByIdAndActivo(adminId, true)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Usuario administrador no autorizado"));
        if (!"ADMIN".equalsIgnoreCase(actor.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede ejecutar simulaciones");
        }
        return actor;
    }

    private PoliticaNegocio getPolicyOrThrow(String policyId) {
        String normalizedPolicyId = normalizeRequired(policyId, "El policyId es obligatorio");
        return politicaNegocioRepository.findById(normalizedPolicyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + normalizedPolicyId));
    }

    private SimulationConfiguration normalizeRunRequest(SimulationRunRequest request) {
        if (request == null) {
            return new SimulationConfiguration(
                    DEFAULT_INSTANCES,
                    DEFAULT_BASE_NODE_DURATION_HOURS,
                    DEFAULT_VARIABILITY_PERCENT,
                    false,
                    null
            );
        }
        int instances = request.getInstances() == null ? DEFAULT_INSTANCES : request.getInstances();
        if (instances <= 0 || instances > MAX_INSTANCES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "instances debe estar entre 1 y " + MAX_INSTANCES);
        }

        double baseNodeDurationHours = request.getBaseNodeDurationHours() == null
                ? DEFAULT_BASE_NODE_DURATION_HOURS
                : request.getBaseNodeDurationHours();
        if (baseNodeDurationHours <= 0.0d) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "baseNodeDurationHours debe ser mayor a 0");
        }

        double variabilityPercent = request.getVariabilityPercent() == null
                ? DEFAULT_VARIABILITY_PERCENT
                : request.getVariabilityPercent();
        if (variabilityPercent < 0.0d || variabilityPercent > 100.0d) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "variabilityPercent debe estar entre 0 y 100");
        }

        return new SimulationConfiguration(
                instances,
                baseNodeDurationHours,
                variabilityPercent,
                Boolean.TRUE.equals(request.getIncludeAiAnalysis()),
                request.getRandomSeed()
        );
    }

    private ComparisonConfiguration normalizeComparisonRequest(SimulationComparisonRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar los datos de comparacion");
        }
        SimulationConfiguration config = normalizeRunRequest(SimulationRunRequest.builder()
                .instances(request.getInstances())
                .baseNodeDurationHours(request.getBaseNodeDurationHours())
                .variabilityPercent(request.getVariabilityPercent())
                .includeAiAnalysis(request.getIncludeAiAnalysis())
                .randomSeed(request.getRandomSeed())
                .build());

        return new ComparisonConfiguration(
                normalizeRequired(request.getFirstPolicyId(), "firstPolicyId es obligatorio"),
                normalizeRequired(request.getSecondPolicyId(), "secondPolicyId es obligatorio"),
                config.instances(),
                config.baseNodeDurationHours(),
                config.variabilityPercent(),
                config.includeAiAnalysis(),
                config.randomSeed()
        );
    }

    private SimulationRunResponse toResponse(SimulationRun run) {
        return SimulationRunResponse.builder()
                .simulationId(run.getId())
                .policyId(run.getPolicyId())
                .policyName(run.getPolicyName())
                .instances(run.getInstances())
                .baseNodeDurationHours(run.getBaseNodeDurationHours())
                .variabilityPercent(run.getVariabilityPercent())
                .includeAiAnalysis(run.isIncludeAiAnalysis())
                .randomSeed(run.getRandomSeed())
                .createdBy(run.getCreatedBy())
                .createdAt(run.getCreatedAt())
                .result(run.getResult())
                .build();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String safeText(String preferred, String fallback) {
        String normalizedPreferred = normalize(preferred);
        if (normalizedPreferred != null) {
            return normalizedPreferred;
        }
        String normalizedFallback = normalize(fallback);
        return normalizedFallback != null ? normalizedFallback : "sin nombre";
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private record GraphContext(
            Map<String, Nodo> nodesById,
            Map<String, List<String>> transitionsByOrigin,
            String startNodeId
    ) {
    }

    private record SimulationConfiguration(
            int instances,
            double baseNodeDurationHours,
            double variabilityPercent,
            boolean includeAiAnalysis,
            Long randomSeed
    ) {
        private SimulationConfiguration withAi(boolean includeAiAnalysis) {
            return new SimulationConfiguration(instances, baseNodeDurationHours, variabilityPercent, includeAiAnalysis, randomSeed);
        }
    }

    private record ComparisonConfiguration(
            String firstPolicyId,
            String secondPolicyId,
            int instances,
            double baseNodeDurationHours,
            double variabilityPercent,
            boolean includeAiAnalysis,
            Long randomSeed
    ) {
    }

    private static final class NodeAccumulator {
        private final Nodo node;
        private long executions;
        private double totalEstimatedTimeHours;

        private NodeAccumulator(Nodo node) {
            this.node = node;
        }

        private void add(double nodeDuration) {
            executions++;
            totalEstimatedTimeHours += nodeDuration;
        }

        private Nodo node() {
            return node;
        }

        private long executions() {
            return executions;
        }

        private double totalEstimatedTimeHours() {
            return totalEstimatedTimeHours;
        }

        private double averageEstimatedTimeHours() {
            return executions == 0 ? 0.0d : totalEstimatedTimeHours / executions;
        }
    }

    private static final class DecisionAccumulator {
        private final Nodo node;
        private long totalDecisions;
        private final Map<String, Long> outcomes = new LinkedHashMap<>();

        private DecisionAccumulator(Nodo node) {
            this.node = node;
        }

        private void recordOutcome(String nodeId) {
            totalDecisions++;
            outcomes.merge(nodeId, 1L, Long::sum);
        }

        private Nodo node() {
            return node;
        }

        private long totalDecisions() {
            return totalDecisions;
        }

        private Map<String, Long> outcomes() {
            return outcomes;
        }
    }
}
