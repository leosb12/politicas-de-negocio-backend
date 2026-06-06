package com.leo.politicas_de_negocio.iaeditorflujo.validator;

import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditOperationDto;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditProposalResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditValidationResult;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class WorkflowAiEditValidator {

    private static final Set<String> ALLOWED_OPERATION_TYPES = Set.of(
            "ADD_NODE",
            "UPDATE_NODE",
            "DELETE_NODE",
            "ADD_TRANSITION",
            "UPDATE_TRANSITION",
            "DELETE_TRANSITION",
            "ASSIGN_RESPONSIBLE",
            "UPDATE_FORM",
            "ADD_FORM_FIELD",
            "DELETE_FORM_FIELD",
            "RENAME_NODE",
            "REMOVE_RESPONSIBLE",
            "CREATE_LOOP",
            "UPDATE_DECISION_CONDITION",
            "MOVE_NODE",
            "REORDER_FLOW",
            "ADD_BUSINESS_RULE",
            "DELETE_BUSINESS_RULE",
            "ADD_INITIAL_REQUIREMENT",
            "UPDATE_INITIAL_REQUIREMENT",
            "DELETE_INITIAL_REQUIREMENT"
    );

    public WorkflowAiEditValidationResult validate(PoliticaNegocio politica, WorkflowAiEditProposalResponse proposal) {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (politica == null) {
            errors.add("No se encontro la politica real para validar la propuesta.");
            return build(warnings, errors);
        }

        if (proposal == null) {
            errors.add("No se recibio propuesta de edicion desde el servicio de IA.");
            return build(warnings, errors);
        }

        if (politica.getNodos() == null || politica.getNodos().isEmpty()) {
            warnings.add("La politica no tiene nodos registrados; la propuesta podria estar incompleta.");
        }

        List<WorkflowAiEditOperationDto> operations = proposal.getOperations();
        if (operations == null || operations.isEmpty()) {
            warnings.add("La IA no devolvio operaciones concretas para previsualizar.");
            return build(warnings, errors);
        }

        Set<String> nodeNames = extractNodeNames(politica.getNodos());
        Set<String> proposedNodeNames = collectProposedNodeNames(operations);
        Set<String> resolvableNodeNames = new LinkedHashSet<>(nodeNames);
        resolvableNodeNames.addAll(proposedNodeNames);
        for (int i = 0; i < operations.size(); i++) {
            WorkflowAiEditOperationDto operation = operations.get(i);
            validateOperation(operation, i, nodeNames, resolvableNodeNames, warnings, errors);
        }

        return build(warnings, errors);
    }

    private void validateOperation(
            WorkflowAiEditOperationDto operation,
            int index,
            Set<String> nodeNames,
            Set<String> resolvableNodeNames,
            List<String> warnings,
            List<String> errors
    ) {
        if (operation == null) {
            errors.add("La operacion en posicion " + index + " es nula.");
            return;
        }

        String type = normalize(operation.getType());
        if (type == null) {
            errors.add("La operacion en posicion " + index + " no incluye type.");
            return;
        }

        String normalizedType = normalizeOperationType(type);
        if (!ALLOWED_OPERATION_TYPES.contains(normalizedType)) {
            warnings.add("La operacion " + humanLabel(operation, index) + " usa un type no reconocido: " + type);
        }

        validateNodeReference(operation, index, "fromNodeName", readNodeReference(operation, "fromNodeName"), resolvableNodeNames, errors);
        validateNodeReference(operation, index, "toNodeName", readNodeReference(operation, "toNodeName"), resolvableNodeNames, errors);

        String nodeName = readNodeReference(operation, "nodeName");
        if ("ADD_NODE".equals(normalizedType)) {
            validateNewNodeName(operation, index, nodeName, nodeNames, errors);
        } else {
            validateNodeReference(operation, index, "nodeName", nodeName, resolvableNodeNames, errors);
        }

        validateNodeReference(operation, index, "targetNodeName", readNodeReference(operation, "targetNodeName"), resolvableNodeNames, errors);
    }

    private void validateNewNodeName(
            WorkflowAiEditOperationDto operation,
            int index,
            String nodeName,
            Set<String> existingNodeNames,
            List<String> errors
    ) {
        if (nodeName == null) {
            errors.add("La operacion " + humanLabel(operation, index) + " requiere nodeName para el nuevo nodo.");
            return;
        }
        if (existingNodeNames.contains(nodeName)) {
            errors.add("La operacion " + humanLabel(operation, index)
                    + " intenta crear nodeName='" + nodeName + "' pero ese nodo ya existe en la politica actual.");
        }
    }

    private void validateNodeReference(
            WorkflowAiEditOperationDto operation,
            int index,
            String fieldName,
            String nodeName,
            Set<String> nodeNames,
            List<String> errors
    ) {
        if (nodeName == null) {
            return;
        }
        if (!nodeNames.contains(nodeName)) {
            errors.add("La operacion " + humanLabel(operation, index)
                    + " referencia " + fieldName + "='" + nodeName + "' pero ese nodo no existe en la politica actual.");
        }
    }

    private String humanLabel(WorkflowAiEditOperationDto operation, int index) {
        String type = normalize(operation.getType());
        return (type != null ? type : "sin-type") + " #" + index;
    }

    private String readNodeReference(WorkflowAiEditOperationDto operation, String fieldName) {
        String directValue = switch (fieldName) {
            case "fromNodeName" -> normalize(operation.getFromNodeName());
            case "toNodeName" -> normalize(operation.getToNodeName());
            case "nodeName" -> normalize(operation.getNodeName());
            case "targetNodeName" -> normalize(operation.getTargetNodeName());
            default -> null;
        };
        if (directValue != null) {
            return directValue;
        }

        Object alternativeValue = operation.property(fieldName);
        if (alternativeValue instanceof String text) {
            return normalize(text);
        }
        Object payload = operation.property("payload");
        if (payload instanceof java.util.Map<?, ?> payloadMap) {
            Object payloadValue = payloadMap.get(fieldName);
            if (payloadValue instanceof String text) {
                return normalize(text);
            }
            for (String alias : aliasesFor(fieldName)) {
                Object aliasValue = payloadMap.get(alias);
                if (aliasValue instanceof String text) {
                    return normalize(text);
                }
            }
        }
        for (String alias : aliasesFor(fieldName)) {
            Object aliasValue = operation.property(alias);
            if (aliasValue instanceof String text) {
                return normalize(text);
            }
        }
        return null;
    }

    private List<String> aliasesFor(String fieldName) {
        return switch (fieldName) {
            case "fromNodeName" -> List.of("from_node_name", "fromNode", "sourceNodeName", "sourceNode");
            case "toNodeName" -> List.of("to_node_name", "toNode", "targetNode");
            case "nodeName" -> List.of("name", "activityName", "node_name", "activity_name");
            case "targetNodeName" -> List.of("target_node_name");
            default -> List.of();
        };
    }

    private String normalizeOperationType(String rawType) {
        String normalized = normalize(rawType);
        if (normalized == null) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CREATE_NODE", "CREAR_NODO", "INSERT_BETWEEN", "INSERTAR_ENTRE" -> "ADD_NODE";
            case "CONNECT", "CONECTAR", "CREATE_EDGE" -> "ADD_TRANSITION";
            case "DISCONNECT", "DESCONECTAR", "DELETE_EDGE", "ELIMINAR_CONEXION" -> "DELETE_TRANSITION";
            case "CHANGE_RESPONSIBLE", "CAMBIAR_RESPONSABLE" -> "ASSIGN_RESPONSIBLE";
            case "QUITAR_RESPONSABLE", "REMOVE_ASSIGNEE" -> "REMOVE_RESPONSIBLE";
            case "ADD_FIELD", "AGREGAR_CAMPO_FORMULARIO" -> "ADD_FORM_FIELD";
            case "DELETE_FIELD", "ELIMINAR_CAMPO_FORMULARIO" -> "DELETE_FORM_FIELD";
            case "EDIT_FIELD", "EDITAR_CAMPO_FORMULARIO" -> "UPDATE_FORM";
            case "REORDER_SEQUENCE", "REORDENAR_FLUJO" -> "REORDER_FLOW";
            case "ADD_INITIAL_REQUIREMENT", "ADD_REQUIREMENT", "AGREGAR_REQUISITO", "AGREGAR_REQUISITO_INICIAL" -> "ADD_INITIAL_REQUIREMENT";
            case "DELETE_INITIAL_REQUIREMENT", "DELETE_REQUIREMENT", "ELIMINAR_REQUISITO", "ELIMINAR_REQUISITO_INICIAL" -> "DELETE_INITIAL_REQUIREMENT";
            case "UPDATE_INITIAL_REQUIREMENT", "UPDATE_REQUIREMENT", "EDITAR_REQUISITO", "EDITAR_REQUISITO_INICIAL" -> "UPDATE_INITIAL_REQUIREMENT";
            default -> upper;
        };
    }


    private Set<String> extractNodeNames(List<Nodo> nodos) {
        Set<String> nodeNames = new LinkedHashSet<>();
        for (Nodo nodo : nodos) {
            if (nodo == null) {
                continue;
            }
            String name = normalize(nodo.getNombre());
            if (name != null) {
                nodeNames.add(name);
            }
        }
        return nodeNames;
    }

    private Set<String> collectProposedNodeNames(List<WorkflowAiEditOperationDto> operations) {
        Set<String> proposedNodeNames = new LinkedHashSet<>();
        for (WorkflowAiEditOperationDto operation : operations) {
            if (operation == null) {
                continue;
            }
            String type = normalize(operation.getType());
            if (!"ADD_NODE".equalsIgnoreCase(type)) {
                continue;
            }
            String nodeName = readNodeReference(operation, "nodeName");
            if (nodeName != null) {
                proposedNodeNames.add(nodeName);
            }
        }
        return proposedNodeNames;
    }

    private WorkflowAiEditValidationResult build(List<String> warnings, List<String> errors) {
        return WorkflowAiEditValidationResult.builder()
                .valid(errors.isEmpty())
                .warnings(List.copyOf(warnings))
                .errors(List.copyOf(errors))
                .build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
