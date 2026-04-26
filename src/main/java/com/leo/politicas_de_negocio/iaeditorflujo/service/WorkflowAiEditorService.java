package com.leo.politicas_de_negocio.iaeditorflujo.service;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.iaeditorflujo.client.WorkflowAiEditorClient;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditIaRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditOperationDto;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditProposalResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditValidationResult;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditWorkflowDto;
import com.leo.politicas_de_negocio.iaeditorflujo.validator.WorkflowAiEditValidator;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowAiEditorService {

    private final WorkflowAiEditorClient workflowAiEditorClient;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final WorkflowAiEditValidator workflowAiEditValidator;

    public WorkflowAiEditPreviewResponse previewEdition(
            String adminUserId,
            String policyId,
            WorkflowAiEditPreviewRequest request
    ) {
        assertAdmin(adminUserId);
        String prompt = normalize(request != null ? request.getPrompt() : null);
        if (prompt == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar un prompt para previsualizar la edicion del flujo");
        }

        PoliticaNegocio politica = findPolicy(policyId);
        WorkflowAiEditProposalResponse proposal = fetchProposal(politica, prompt);

        if (proposal == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "No se pudo obtener una propuesta de edicion desde el servicio de IA"
            );
        }

        proposal.setOperations(normalizeOperationsForPolicy(politica, prompt, optionalList(proposal.getOperations())));
        WorkflowAiEditValidationResult validation = workflowAiEditValidator.validate(politica, proposal);

        return WorkflowAiEditPreviewResponse.builder()
                .policyId(politica.getId())
                .policyName(politica.getNombre())
                .success(proposal.isSuccess())
                .valid(validation.isValid())
                .intent(proposal.getIntent())
                .summary(proposal.getSummary())
                .operations(optionalList(proposal.getOperations()))
                .warnings(List.of())
                .errors(mergeMessages(proposal.getErrors(), validation.getErrors()))
                .requiresConfirmation(false)
                .generatedAt(LocalDateTime.now())
                .build();
    }

    public WorkflowAiEditApplyResponse applyEdition(
            String adminUserId,
            String policyId,
            WorkflowAiEditApplyRequest request
    ) {
        assertAdmin(adminUserId);
        PoliticaNegocio politica = findPolicy(policyId);
        String prompt = normalize(request != null ? request.getPrompt() : null);

        List<WorkflowAiEditOperationDto> operations = request != null
                ? optionalList(request.getOperations())
                : List.of();

        if (operations.isEmpty()) {
            if (prompt == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar un prompt para editar el flujo");
            }
            WorkflowAiEditProposalResponse proposal = fetchProposal(politica, prompt);
            operations = proposal != null ? optionalList(proposal.getOperations()) : List.of();
        }

        operations = normalizeOperationsForPolicy(politica, prompt, operations);
        if (operations.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se pudo convertir el prompt en cambios aplicables");
        }

        ApplyContext context = new ApplyContext(politica);
        int applied = applyOperations(context, operations, prompt);
        if (applied == 0 && prompt != null) {
            operations = inferLocalOperations(politica, prompt);
            applied = applyOperations(context, operations, prompt);
        }

        if (applied == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se detectaron cambios aplicables para guardar");
        }

        politica.setNodos(context.nodes);
        politica.setConexiones(context.connections);
        politica.setFechaActualizacion(LocalDateTime.now());
        PoliticaNegocio saved = politicaNegocioRepository.save(politica);

        return WorkflowAiEditApplyResponse.builder()
                .policyId(saved.getId())
                .policyName(saved.getNombre())
                .success(true)
                .message("Cambios aplicados y guardados en la politica.")
                .appliedOperations(applied)
                .operations(operations)
                .appliedAt(LocalDateTime.now())
                .build();
    }

    private WorkflowAiEditProposalResponse fetchProposal(PoliticaNegocio politica, String prompt) {
        return workflowAiEditorClient.previewEdition(
                WorkflowAiEditIaRequest.builder()
                        .workflow(toWorkflowDto(politica))
                        .prompt(prompt)
                        .build()
        );
    }

    private List<WorkflowAiEditOperationDto> normalizeOperationsForPolicy(
            PoliticaNegocio politica,
            String prompt,
            List<WorkflowAiEditOperationDto> operations
    ) {
        WorkflowAiEditProposalResponse proposal = new WorkflowAiEditProposalResponse();
        proposal.setSuccess(true);
        proposal.setIntent("UPDATE_WORKFLOW");
        proposal.setOperations(operations);
        proposal.setWarnings(List.of());
        proposal.setErrors(List.of());

        WorkflowAiEditValidationResult validation = workflowAiEditValidator.validate(politica, proposal);
        if (validation.isValid()) {
            return operations;
        }

        List<WorkflowAiEditOperationDto> fallback = inferLocalOperations(politica, prompt);
        return fallback.isEmpty() ? operations : fallback;
    }

    private int applyOperations(
            ApplyContext context,
            List<WorkflowAiEditOperationDto> operations,
            String prompt
    ) {
        int applied = 0;
        for (WorkflowAiEditOperationDto operation : operations) {
            if (operation == null) {
                continue;
            }

            String type = normalizeUpper(operation.getType());
            if ("ADD_NODE".equals(type)) {
                applied += applyAddNode(context, operation, prompt);
            } else if ("DELETE_TRANSITION".equals(type)) {
                applied += applyDeleteTransition(context, operation);
            } else if ("ADD_TRANSITION".equals(type) || "CREATE_LOOP".equals(type)) {
                applied += applyAddTransition(context, operation);
            } else if ("UPDATE_NODE".equals(type)
                    || "ASSIGN_RESPONSIBLE".equals(type)
                    || "RENAME_NODE".equals(type)) {
                applied += applyUpdateNode(context, operation, prompt);
            } else if ("DELETE_NODE".equals(type)) {
                applied += applyDeleteNode(context, operation);
            }
        }
        return applied;
    }

    private int applyAddNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        String nodeName = firstText(operation, "nodeName", "name", "activityName", "node_name", "activity_name");
        if (nodeName == null) {
            nodeName = extractAddedNodeName(prompt);
        }
        if (nodeName == null || context.findNodeByName(nodeName) != null) {
            return 0;
        }

        Nodo reference = resolveReferenceNode(context, operation, prompt);
        TipoNodo nodeType = parseNodeType(firstText(operation, "nodeType", "typeNode"));
        String nodeId = uniqueNodeId(context, nodeName);
        Nodo newNode = Nodo.builder()
                .id(nodeId)
                .tipo(nodeType)
                .nombre(nodeName)
                .version(0L)
                .fechaActualizacion(LocalDateTime.now())
                .build();

        if (reference != null) {
            newNode.setDepartamentoId(reference.getDepartamentoId());
            if (nodeType == TipoNodo.ACTIVIDAD) {
                newNode.setResponsableTipo(reference.getResponsableTipo());
                newNode.setResponsableId(reference.getResponsableId());
            }
            newNode.setPosX(reference.getPosX() != null ? reference.getPosX() + 260 : null);
            newNode.setPosY(reference.getPosY());
        }
        if (newNode.getPosX() == null) {
            newNode.setPosX(180.0 + context.nodes.size() * 220.0);
        }
        if (newNode.getPosY() == null) {
            newNode.setPosY(180.0);
        }

        context.nodes.add(newNode);
        String position = normalizeUpper(firstText(operation, "position"));
        if ("BEFORE".equals(position)) {
            insertBeforeReference(context, newNode, reference);
        } else {
            insertAfterReference(context, newNode, reference);
        }
        return 1;
    }

    private int applyDeleteTransition(ApplyContext context, WorkflowAiEditOperationDto operation) {
        String fromId = resolveNodeId(context, firstText(operation, "fromNodeId", "fromId"), firstText(operation, "fromNodeName", "fromNode"));
        String toId = resolveNodeId(context, firstText(operation, "toNodeId", "toId"), firstText(operation, "toNodeName", "toNode"));
        if (fromId == null || toId == null) {
            return 0;
        }
        return context.connections.removeIf(connection -> fromId.equals(connection.getOrigen()) && toId.equals(connection.getDestino()))
                ? 1
                : 0;
    }

    private int applyAddTransition(ApplyContext context, WorkflowAiEditOperationDto operation) {
        String fromId = resolveNodeId(context, firstText(operation, "fromNodeId", "fromId"), firstText(operation, "fromNodeName", "fromNode"));
        String toId = resolveNodeId(context, firstText(operation, "toNodeId", "toId"), firstText(operation, "toNodeName", "toNode"));
        if (fromId == null || toId == null || context.hasConnection(fromId, toId)) {
            return 0;
        }
        context.connections.add(Conexion.builder().origen(fromId).destino(toId).build());
        return 1;
    }

    private int applyUpdateNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveOperationNode(context, operation, prompt);
        if (node == null) {
            return 0;
        }

        int applied = 0;
        String newName = firstText(operation, "newName", "new_name");
        if (newName != null && !newName.equals(node.getNombre())) {
            node.setNombre(newName);
            applied++;
        }

        String departamentoId = resolveDepartmentId(operation, prompt);
        if (departamentoId != null) {
            node.setResponsableTipo("DEPARTAMENTO");
            node.setResponsableId(departamentoId);
            if (normalize(node.getDepartamentoId()) == null) {
                node.setDepartamentoId(departamentoId);
            }
            applied++;
        }

        if (applied > 0) {
            node.setFechaActualizacion(LocalDateTime.now());
            node.setVersion(node.getVersion() != null ? node.getVersion() + 1 : 1L);
        }
        return applied;
    }

    private int applyDeleteNode(ApplyContext context, WorkflowAiEditOperationDto operation) {
        Nodo node = resolveOperationNode(context, operation, null);
        if (node == null || node.getTipo() == TipoNodo.INICIO || node.getTipo() == TipoNodo.FIN) {
            return 0;
        }
        boolean removed = context.nodes.removeIf(candidate -> node.getId().equals(candidate.getId()));
        if (removed) {
            context.connections.removeIf(connection ->
                    node.getId().equals(connection.getOrigen()) || node.getId().equals(connection.getDestino()));
        }
        return removed ? 1 : 0;
    }

    private void insertAfterReference(ApplyContext context, Nodo newNode, Nodo reference) {
        if (reference == null) {
            return;
        }
        Optional<Conexion> outgoing = context.connections.stream()
                .filter(connection -> reference.getId().equals(connection.getOrigen()))
                .findFirst();
        outgoing.ifPresent(context.connections::remove);
        addConnectionIfMissing(context, reference.getId(), newNode.getId());
        outgoing.ifPresent(connection -> addConnectionIfMissing(context, newNode.getId(), connection.getDestino()));
    }

    private void insertBeforeReference(ApplyContext context, Nodo newNode, Nodo reference) {
        if (reference == null) {
            return;
        }
        Optional<Conexion> incoming = context.connections.stream()
                .filter(connection -> reference.getId().equals(connection.getDestino()))
                .findFirst();
        incoming.ifPresent(context.connections::remove);
        incoming.ifPresent(connection -> addConnectionIfMissing(context, connection.getOrigen(), newNode.getId()));
        addConnectionIfMissing(context, newNode.getId(), reference.getId());
    }

    private void addConnectionIfMissing(ApplyContext context, String fromId, String toId) {
        if (fromId != null && toId != null && !context.hasConnection(fromId, toId)) {
            context.connections.add(Conexion.builder().origen(fromId).destino(toId).build());
        }
    }

    private Nodo resolveReferenceNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        String referenceId = firstText(operation, "referenceNodeId", "referenceId");
        String referenceName = firstText(operation, "referenceNodeName", "referenceName");
        Nodo reference = context.findNode(referenceId, referenceName);
        if (reference != null) {
            return reference;
        }
        return inferDefaultInsertionReference(context, prompt);
    }

    private Nodo resolveOperationNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = context.findNode(
                firstText(operation, "nodeId", "targetNodeId"),
                firstText(operation, "nodeName", "targetNodeName", "name", "activityName")
        );
        if (node != null) {
            return node;
        }
        if (prompt == null) {
            return null;
        }
        return context.findBestNodeMentionedIn(prompt);
    }

    private String resolveNodeId(ApplyContext context, String nodeId, String nodeName) {
        Nodo node = context.findNode(nodeId, nodeName);
        return node != null ? node.getId() : null;
    }

    private Nodo inferDefaultInsertionReference(ApplyContext context, String prompt) {
        String normalizedPrompt = normalizeForSearch(prompt);
        if (normalizedPrompt.contains("foto") || normalizedPrompt.contains("document")) {
            Nodo dataNode = context.findNodeByTokens("solicitar", "datos");
            if (dataNode != null) {
                return dataNode;
            }
        }
        Nodo start = context.firstByType(TipoNodo.INICIO);
        if (start != null) {
            return context.connections.stream()
                    .filter(connection -> start.getId().equals(connection.getOrigen()))
                    .map(connection -> context.findNode(connection.getDestino(), null))
                    .filter(candidate -> candidate != null)
                    .findFirst()
                    .orElse(start);
        }
        return context.nodes.isEmpty() ? null : context.nodes.get(0);
    }

    private TipoNodo parseNodeType(String rawType) {
        String normalized = normalizeForSearch(rawType);
        if (normalized.contains("decision")) {
            return TipoNodo.DECISION;
        }
        if (normalized.contains("fork")) {
            return TipoNodo.FORK;
        }
        if (normalized.contains("join")) {
            return TipoNodo.JOIN;
        }
        if (normalized.contains("fin") || normalized.contains("end")) {
            return TipoNodo.FIN;
        }
        if (normalized.contains("inicio") || normalized.contains("start")) {
            return TipoNodo.INICIO;
        }
        return TipoNodo.ACTIVIDAD;
    }

    private String resolveDepartmentId(WorkflowAiEditOperationDto operation, String prompt) {
        String explicitId = firstText(
                operation,
                "responsibleRoleId",
                "responsibleId",
                "responsableId",
                "departmentId",
                "departamentoId"
        );
        if (explicitId != null) {
            return explicitId;
        }

        String departmentName = firstText(
                operation,
                "responsibleRoleName",
                "responsibleName",
                "responsableNombre",
                "departmentHint",
                "departmentName",
                "departamentoNombre"
        );
        if (departmentName == null) {
            departmentName = extractDepartmentName(prompt);
        }
        if (departmentName == null) {
            return null;
        }

        Optional<Departamento> exact = departamentoRepository.findByNombreIgnoreCase(departmentName);
        if (exact.isPresent()) {
            return exact.get().getId();
        }

        String normalizedNeedle = normalizeForSearch(departmentName);
        return departamentoRepository.findAll().stream()
                .filter(departamento -> normalizeForSearch(departamento.getNombre()).contains(normalizedNeedle)
                        || normalizedNeedle.contains(normalizeForSearch(departamento.getNombre())))
                .map(Departamento::getId)
                .findFirst()
                .orElse(null);
    }

    private List<WorkflowAiEditOperationDto> inferLocalOperations(PoliticaNegocio politica, String prompt) {
        List<WorkflowAiEditOperationDto> operations = new ArrayList<>();
        String normalizedPrompt = normalizeForSearch(prompt);
        if (normalizedPrompt.isEmpty()) {
            return operations;
        }

        if (normalizedPrompt.contains("responsable")) {
            WorkflowAiEditOperationDto operation = new WorkflowAiEditOperationDto();
            operation.setType("ASSIGN_RESPONSIBLE");
            Nodo node = new ApplyContext(politica).findBestNodeMentionedIn(prompt);
            if (node != null) {
                operation.setNodeName(node.getNombre());
            }
            String departmentName = extractDepartmentName(prompt);
            if (departmentName != null) {
                operation.addProperty("departmentHint", departmentName);
            }
            operations.add(operation);
            return operations;
        }

        if (normalizedPrompt.contains("agrega")
                || normalizedPrompt.contains("agregar")
                || normalizedPrompt.contains("anade")
                || normalizedPrompt.contains("anadime")
                || normalizedPrompt.contains("crea")
                || normalizedPrompt.contains("crear")) {
            String nodeName = extractAddedNodeName(prompt);
            if (nodeName != null) {
                WorkflowAiEditOperationDto operation = new WorkflowAiEditOperationDto();
                operation.setType("ADD_NODE");
                operation.setNodeName(nodeName);
                Nodo reference = inferDefaultInsertionReference(new ApplyContext(politica), prompt);
                if (reference != null) {
                    operation.addProperty("referenceNodeName", reference.getNombre());
                    operation.addProperty("position", "after");
                }
                operations.add(operation);
            }
        }
        return operations;
    }

    private String extractAddedNodeName(String prompt) {
        String text = normalize(prompt);
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(
                "(?:nodo|actividad|tarea)\\s+(?:llamad[ao]\\s+)?(.+?)(?:\\s+(?:despues|antes|entre|para\\s+el|para\\s+la)\\b|$)",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (matcher.find()) {
            return title(matcher.group(1));
        }
        if (normalizeForSearch(text).contains("foto")) {
            return "Pedir foto";
        }
        return null;
    }

    private String extractDepartmentName(String prompt) {
        String text = normalize(prompt);
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(
                "(?:pone|pon|asigna|a|al departamento|departamento)\\s+([\\p{L}\\p{N} ._-]+)$",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (matcher.find()) {
            return title(matcher.group(1));
        }
        return null;
    }

    private String firstText(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            String direct = directOperationValue(operation, name);
            if (direct != null) {
                return direct;
            }
            Object value = operation.property(name);
            if (value instanceof String text) {
                String normalized = normalize(text);
                if (normalized != null) {
                    return normalized;
                }
            }
        }
        return null;
    }

    private String directOperationValue(WorkflowAiEditOperationDto operation, String name) {
        return switch (name) {
            case "type" -> normalize(operation.getType());
            case "fromNodeName" -> normalize(operation.getFromNodeName());
            case "toNodeName" -> normalize(operation.getToNodeName());
            case "nodeName", "name", "activityName" -> normalize(operation.getNodeName());
            case "targetNodeName" -> normalize(operation.getTargetNodeName());
            case "responsableTipo" -> normalize(operation.getResponsableTipo());
            case "responsibleTipo" -> normalize(operation.getResponsibleTipo());
            case "responsableId" -> normalize(operation.getResponsableId());
            case "responsibleId" -> normalize(operation.getResponsibleId());
            default -> null;
        };
    }

    private String uniqueNodeId(ApplyContext context, String nodeName) {
        String base = normalizeForSearch(nodeName).replaceAll("[^a-z0-9]+", "_");
        if (base.isBlank()) {
            base = "nodo_" + UUID.randomUUID().toString().substring(0, 8);
        }
        String candidate = base;
        int suffix = 2;
        while (context.findNode(candidate, null) != null) {
            candidate = base + "_" + suffix;
            suffix++;
        }
        return candidate;
    }

    private PoliticaNegocio findPolicy(String policyId) {
        String normalizedPolicyId = normalize(policyId);
        if (normalizedPolicyId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El policyId es obligatorio");
        }
        return politicaNegocioRepository.findById(normalizedPolicyId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Politica no encontrada con ID: " + normalizedPolicyId));
    }

    private Usuario assertAdmin(String adminUserId) {
        String userId = normalize(adminUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Debe enviar el header X-Admin-User-Id");
        }

        Usuario admin = usuarioRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Administrador no autorizado"));

        if (admin.getRol() == null || !"ADMIN".equalsIgnoreCase(admin.getRol())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Solo un ADMIN puede realizar esta accion");
        }

        return admin;
    }

    private WorkflowAiEditWorkflowDto toWorkflowDto(PoliticaNegocio politica) {
        return WorkflowAiEditWorkflowDto.builder()
                .policyId(politica.getId())
                .policyName(politica.getNombre())
                .description(politica.getDescripcion())
                .status(politica.getEstado() != null ? politica.getEstado().name() : null)
                .policyType(politica.getTipoPolitica() != null ? politica.getTipoPolitica().name() : null)
                .departamentoInicioId(politica.getDepartamentoInicioId())
                .laneOrientation(politica.getLaneOrientation())
                .laneWidth(politica.getLaneWidth())
                .laneHeight(politica.getLaneHeight())
                .nodos(optionalList(politica.getNodos()))
                .conexiones(optionalList(politica.getConexiones()))
                .build();
    }

    private <T> List<T> optionalList(List<T> items) {
        return items != null ? items : List.of();
    }

    private List<String> mergeMessages(List<String> primary, List<String> secondary) {
        Set<String> merged = new LinkedHashSet<>();
        addAllNormalized(merged, primary);
        addAllNormalized(merged, secondary);
        return new ArrayList<>(merged);
    }

    private void addAllNormalized(Set<String> target, List<String> source) {
        if (source == null) {
            return;
        }
        for (String item : source) {
            String normalized = normalize(item);
            if (normalized != null) {
                target.add(normalized);
            }
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeUpper(String value) {
        String normalized = normalize(value);
        return normalized != null ? normalized.toUpperCase() : null;
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private String title(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return null;
        }
        String[] parts = normalized.split("\\s+");
        List<String> titled = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            titled.add(part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase());
        }
        return String.join(" ", titled);
    }

    private class ApplyContext {
        private final List<Nodo> nodes;
        private final List<Conexion> connections;

        private ApplyContext(PoliticaNegocio politica) {
            this.nodes = new ArrayList<>(optionalList(politica.getNodos()));
            this.connections = new ArrayList<>(optionalList(politica.getConexiones()));
        }

        private Nodo findNode(String nodeId, String nodeName) {
            String normalizedId = normalize(nodeId);
            if (normalizedId != null) {
                for (Nodo node : nodes) {
                    if (normalizedId.equals(node.getId())) {
                        return node;
                    }
                }
            }
            return findNodeByName(nodeName);
        }

        private Nodo findNodeByName(String nodeName) {
            String normalizedName = normalizeForSearch(nodeName);
            if (normalizedName.isEmpty()) {
                return null;
            }
            Nodo containsMatch = null;
            for (Nodo node : nodes) {
                String candidate = normalizeForSearch(node.getNombre());
                if (candidate.equals(normalizedName)) {
                    return node;
                }
                if (candidate.contains(normalizedName) || normalizedName.contains(candidate)) {
                    containsMatch = node;
                }
            }
            return containsMatch;
        }

        private Nodo findBestNodeMentionedIn(String prompt) {
            String normalizedPrompt = normalizeForSearch(prompt);
            Nodo best = null;
            int bestScore = 0;
            for (Nodo node : nodes) {
                String candidate = normalizeForSearch(node.getNombre());
                int score = 0;
                for (String token : candidate.split(" ")) {
                    if (token.length() >= 4 && normalizedPrompt.contains(token)) {
                        score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = node;
                }
            }
            return bestScore > 0 ? best : null;
        }

        private Nodo findNodeByTokens(String... tokens) {
            for (Nodo node : nodes) {
                String candidate = normalizeForSearch(node.getNombre());
                boolean matches = true;
                for (String token : tokens) {
                    if (!candidate.contains(token)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return node;
                }
            }
            return null;
        }

        private Nodo firstByType(TipoNodo type) {
            return nodes.stream()
                    .filter(node -> node.getTipo() == type)
                    .findFirst()
                    .orElse(null);
        }

        private boolean hasConnection(String fromId, String toId) {
            return connections.stream()
                    .anyMatch(connection -> fromId.equals(connection.getOrigen()) && toId.equals(connection.getDestino()));
        }
    }
}
