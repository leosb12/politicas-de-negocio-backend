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
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.CondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.GrupoCondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.ReglaCondicionDecision;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WorkflowAiEditorService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAiEditorService.class);
    private static final String RESPONSABLE_INICIADOR_TRAMITE_ID = "__RESPONSABLE_INICIADOR_TRAMITE__";
    private static final String RESPONSABLE_USUARIO_FINAL_ID = "__RESPONSABLE_USUARIO_FINAL__";

    private final WorkflowAiEditorClient workflowAiEditorClient;
    private final PoliticaNegocioRepository politicaNegocioRepository;
    private final UsuarioRepository usuarioRepository;
    private final DepartamentoRepository departamentoRepository;
    private final WorkflowAiEditValidator workflowAiEditValidator;

    private record ResponsibleResolution(String type, String id) {
    }

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
                .warnings(mergeMessages(proposal.getWarnings(), validation.getWarnings()))
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
            context = new ApplyContext(politica);
            applied = applyOperations(context, operations, prompt);
        }

        if (applied == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No se detectaron cambios aplicables para guardar");
        }

        context.removeBrokenConnections();
        String blockingError = context.findBlockingGraphError();
        if (blockingError != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, blockingError);
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
                .workflow(saved)
                .warnings(context.warnings)
                .errors(context.errors)
                .appliedAt(LocalDateTime.now())
                .build();
    }

    private WorkflowAiEditProposalResponse fetchProposal(PoliticaNegocio politica, String prompt) {
        return workflowAiEditorClient.previewEdition(
                WorkflowAiEditIaRequest.builder()
                        .workflow(toWorkflowDto(politica))
                        .prompt(prompt)
                        .context(toIaContext(politica))
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

            String type = normalizeOperationType(operation.getType());
            if ("ADD_NODE".equals(type)) {
                applied += applyAddNode(context, operation, prompt);
            } else if ("DELETE_TRANSITION".equals(type)) {
                applied += applyDeleteTransition(context, operation);
            } else if ("ADD_TRANSITION".equals(type) || "CREATE_LOOP".equals(type)) {
                applied += applyAddTransition(context, operation);
            } else if ("UPDATE_TRANSITION".equals(type)) {
                applied += applyUpdateTransition(context, operation);
            } else if ("UPDATE_NODE".equals(type)
                    || "ASSIGN_RESPONSIBLE".equals(type)
                    || "RENAME_NODE".equals(type)) {
                applied += applyUpdateNode(context, operation, prompt);
            } else if ("REMOVE_RESPONSIBLE".equals(type)) {
                applied += applyRemoveResponsible(context, operation, prompt);
            } else if ("DELETE_NODE".equals(type)) {
                applied += applyDeleteNode(context, operation);
            } else if ("MOVE_NODE".equals(type)) {
                applied += applyMoveNode(context, operation, prompt);
            } else if ("ADD_FORM_FIELD".equals(type)) {
                applied += applyAddFormField(context, operation, prompt);
            } else if ("DELETE_FORM_FIELD".equals(type)) {
                applied += applyDeleteFormField(context, operation, prompt);
            } else if ("UPDATE_FORM".equals(type)) {
                applied += applyUpdateForm(context, operation, prompt);
            } else if ("UPDATE_DECISION_CONDITION".equals(type)) {
                applied += applyUpdateDecisionCondition(context, operation, prompt);
            } else if ("REORDER_FLOW".equals(type)) {
                applied += applyReorderFlow(context, operation);
            } else {
                context.warn("Operacion IA no soportada y omitida: " + operation.getType());
            }
        }
        return applied;
    }

    private int applyAddNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        String nodeName = firstText(operation, "nodeName", "name", "activityName", "node_name", "activity_name");
        if (nodeName == null) {
            nodeName = extractAddedNodeName(prompt);
        }
        if (nodeName == null) {
            context.warn("ADD_NODE omitido: no se pudo determinar el nombre del nuevo nodo.");
            return 0;
        }
        if (context.findNodeByName(nodeName) != null) {
            context.warn("ADD_NODE omitido: ya existe un nodo llamado " + nodeName + ".");
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
                .formulario(new ArrayList<>())
                .condiciones(new ArrayList<>())
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

        applyExplicitPosition(newNode, operation);
        applyResponsibleToNode(newNode, operation, prompt, context);
        List<CampoFormulario> formFields = extractFormFields(operation);
        if (!formFields.isEmpty() && nodeType == TipoNodo.ACTIVIDAD) {
            newNode.setFormulario(formFields);
        }

        context.nodes.add(newNode);
        if (shouldAutoConnect(operation)) {
            String position = normalizeUpper(firstText(operation, "position"));
            if ("BEFORE".equals(position)) {
                insertBeforeReference(context, newNode, reference);
            } else {
                insertAfterReference(context, newNode, reference);
            }
        }
        applyDecisionOptions(context, newNode, operation);
        log.info("[WF-EDIT] ADD_NODE aplicado policy={} node={} type={}", context.policyId, newNode.getNombre(), newNode.getTipo());
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
            if (fromId == null || toId == null) {
                context.warn("ADD_TRANSITION omitida: origen o destino no existe.");
            }
            return 0;
        }
        Conexion connection = Conexion.builder()
                .origen(fromId)
                .destino(toId)
                .puertoOrigen(firstText(operation, "sourcePort", "fromPort", "puertoOrigen"))
                .puertoDestino(firstText(operation, "targetPort", "toPort", "puertoDestino"))
                .build();
        context.connections.add(connection);
        applyDecisionConditionForConnection(context, connection, operation);
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

        TipoNodo nextType = parseNodeTypeNullable(firstText(operation, "nodeType", "typeNode", "newType", "tipoNodo", "tipo"));
        if (nextType != null && nextType != node.getTipo()) {
            node.setTipo(nextType);
            if (nextType != TipoNodo.ACTIVIDAD) {
                node.setResponsableTipo(null);
                node.setResponsableId(null);
                node.setDepartamentoId(null);
            }
            applied++;
        }

        if (applyExplicitPosition(node, operation)) {
            applied++;
        }

        if (applyResponsibleToNode(node, operation, prompt, context)) {
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
        if (node == null) {
            context.warn("DELETE_NODE omitido: no se encontro el nodo indicado.");
            return 0;
        }
        if (node.getTipo() == TipoNodo.INICIO) {
            context.warn("DELETE_NODE omitido: no se permite eliminar INICIO.");
            return 0;
        }
        if (node.getTipo() == TipoNodo.FIN && context.countByType(TipoNodo.FIN) <= 1) {
            context.warn("DELETE_NODE omitido: no se permite eliminar el unico FIN.");
            return 0;
        }
        boolean removed = context.nodes.removeIf(candidate -> node.getId().equals(candidate.getId()));
        if (removed) {
            context.connections.removeIf(connection ->
                    node.getId().equals(connection.getOrigen()) || node.getId().equals(connection.getDestino()));
        }
        return removed ? 1 : 0;
    }

    private int applyUpdateTransition(ApplyContext context, WorkflowAiEditOperationDto operation) {
        String fromId = resolveNodeId(
                context,
                firstText(operation, "fromNodeId", "fromId"),
                firstText(operation, "fromNodeName", "fromNode")
        );
        String oldToId = resolveNodeId(
                context,
                firstText(operation, "oldToNodeId", "previousToNodeId", "currentToNodeId"),
                firstText(operation, "oldToNodeName", "previousToNodeName", "currentToNodeName")
        );
        String toId = resolveNodeId(
                context,
                firstText(operation, "newToNodeId", "toNodeId", "toId"),
                firstText(operation, "newToNodeName", "toNodeName", "toNode")
        );

        if (fromId == null || oldToId == null || toId == null) {
            context.warn("UPDATE_TRANSITION omitida: faltan origen, destino actual o destino nuevo.");
            return 0;
        }

        boolean removed = context.connections.removeIf(connection ->
                fromId.equals(connection.getOrigen()) && oldToId.equals(connection.getDestino()));
        if (!removed) {
            context.warn("UPDATE_TRANSITION omitida: la conexion actual no existe.");
            return 0;
        }
        addConnectionIfMissing(context, fromId, toId);
        return 1;
    }

    private int applyRemoveResponsible(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveOperationNode(context, operation, prompt);
        if (node == null || node.getTipo() != TipoNodo.ACTIVIDAD) {
            context.warn("REMOVE_RESPONSIBLE omitido: solo aplica a una actividad existente.");
            return 0;
        }
        if (node.getResponsableTipo() == null && node.getResponsableId() == null) {
            return 0;
        }
        node.setResponsableTipo(null);
        node.setResponsableId(null);
        touchNode(node);
        return 1;
    }

    private int applyMoveNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveOperationNode(context, operation, prompt);
        if (node == null || node.getTipo() == TipoNodo.INICIO) {
            context.warn("MOVE_NODE omitido: no se encontro un nodo movible.");
            return 0;
        }

        boolean changed = applyExplicitPosition(node, operation);
        Nodo reference = resolveReferenceNode(context, operation, prompt);
        if (reference != null && !Objects.equals(reference.getId(), node.getId())) {
            String position = normalizeUpper(firstText(operation, "position"));
            moveNodeRelative(context, node, reference, "BEFORE".equals(position));
            changed = true;
        }

        if (changed) {
            touchNode(node);
            return 1;
        }
        return 0;
    }

    private int applyAddFormField(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveFormNode(context, operation, prompt);
        if (node == null || node.getTipo() != TipoNodo.ACTIVIDAD) {
            context.warn("ADD_FORM_FIELD omitido: el formulario debe pertenecer a una actividad existente.");
            return 0;
        }

        String fieldName = extractFieldName(operation, prompt);
        if (fieldName == null) {
            context.warn("ADD_FORM_FIELD omitido: falta el nombre del campo.");
            return 0;
        }

        List<CampoFormulario> form = ensureForm(node);
        if (findFieldIndex(form, fieldName) >= 0) {
            context.warn("ADD_FORM_FIELD omitido: el campo " + fieldName + " ya existe en " + node.getNombre() + ".");
            return 0;
        }

        TipoCampo fieldType = parseFieldType(firstText(operation, "fieldType", "tipoCampo", "type", "tipo"), fieldName);
        CampoFormulario field = CampoFormulario.builder()
                .campo(fieldName)
                .tipo(fieldType)
                .etiqueta(firstText(operation, "label", "etiqueta", "fieldLabel"))
                .requerido(firstNullableBoolean(operation, "required", "requerido", "obligatorio"))
                .placeholder(firstText(operation, "placeholder"))
                .ayuda(firstText(operation, "help", "ayuda", "description", "descripcion"))
                .opciones(firstStringList(operation, "options", "opciones"))
                .validaciones(firstMap(operation, "validations", "validaciones"))
                .build();
        if (field.getRequerido() == null) {
            field.setRequerido(true);
        }
        form.add(field);
        touchNode(node);
        return 1;
    }

    private int applyDeleteFormField(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveFormNode(context, operation, prompt);
        if (node == null || node.getFormulario() == null) {
            context.warn("DELETE_FORM_FIELD omitido: no se encontro el formulario de la actividad.");
            return 0;
        }
        String fieldName = extractFieldName(operation, prompt);
        if (fieldName == null) {
            context.warn("DELETE_FORM_FIELD omitido: falta el campo a eliminar.");
            return 0;
        }

        int before = node.getFormulario().size();
        node.setFormulario(node.getFormulario().stream()
                .filter(field -> !sameText(field.getCampo(), fieldName))
                .toList());
        if (node.getFormulario().size() == before) {
            context.warn("DELETE_FORM_FIELD omitido: no existe el campo " + fieldName + ".");
            return 0;
        }
        touchNode(node);
        return 1;
    }

    private int applyUpdateForm(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveFormNode(context, operation, prompt);
        if (node == null || node.getFormulario() == null) {
            context.warn("UPDATE_FORM omitido: no se encontro el formulario de la actividad.");
            return 0;
        }

        String fieldName = extractFieldName(operation, prompt);
        if (fieldName == null) {
            context.warn("UPDATE_FORM omitido: falta el campo a modificar.");
            return 0;
        }

        int index = findFieldIndex(node.getFormulario(), fieldName);
        if (index < 0) {
            context.warn("UPDATE_FORM omitido: no existe el campo " + fieldName + ".");
            return 0;
        }

        CampoFormulario field = node.getFormulario().get(index);
        int applied = 0;
        String newName = firstText(operation, "newName", "newFieldName", "newLabel", "label");
        if (newName != null && !sameText(newName, field.getCampo())) {
            if (findFieldIndex(node.getFormulario(), newName) >= 0) {
                context.warn("UPDATE_FORM no renombro el campo porque ya existe " + newName + ".");
            } else {
                field.setCampo(newName);
                applied++;
            }
        }

        String rawType = firstText(operation, "fieldType", "newFieldType", "tipoCampo", "type", "tipo");
        if (rawType != null) {
            TipoCampo nextType = parseFieldType(rawType, field.getCampo());
            if (nextType != field.getTipo()) {
                field.setTipo(nextType);
                applied++;
            }
        }

        if (applyFieldMetadata(field, operation)) {
            applied++;
        }

        if (applied > 0) {
            touchNode(node);
        }
        return applied;
    }

    private int applyUpdateDecisionCondition(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveOperationNode(context, operation, prompt);
        if (node == null || node.getTipo() != TipoNodo.DECISION) {
            context.warn("UPDATE_DECISION_CONDITION omitido: se requiere un nodo DECISION.");
            return 0;
        }

        String targetId = resolveNodeId(
                context,
                firstText(operation, "toNodeId", "targetNodeId", "siguiente"),
                firstText(operation, "toNodeName", "targetNodeName", "targetNode")
        );
        String condition = firstText(operation, "condition", "decisionCondition", "resultado");
        if (targetId == null || condition == null) {
            context.warn("UPDATE_DECISION_CONDITION omitido: faltan condicion o siguiente nodo.");
            return 0;
        }

        addOrUpdateDecisionCondition(node, condition, targetId, null);
        touchNode(node);
        return 1;
    }

    private int applyReorderFlow(ApplyContext context, WorkflowAiEditOperationDto operation) {
        List<String> nodeNames = firstStringList(operation, "nodeNames", "sequence", "orderedNodes");
        if (nodeNames.size() < 2) {
            context.warn("REORDER_FLOW omitido: se requieren al menos dos nodos.");
            return 0;
        }

        List<Nodo> sequence = new ArrayList<>();
        for (String nodeName : nodeNames) {
            Nodo node = context.findNode(null, nodeName);
            if (node == null) {
                context.warn("REORDER_FLOW omitido: no existe el nodo " + nodeName + ".");
                return 0;
            }
            sequence.add(node);
        }

        Set<String> selectedIds = new LinkedHashSet<>(sequence.stream().map(Nodo::getId).toList());
        Set<String> allowedPairs = new LinkedHashSet<>();
        for (int i = 0; i < sequence.size() - 1; i++) {
            allowedPairs.add(connectionKey(sequence.get(i).getId(), sequence.get(i + 1).getId()));
        }

        context.connections.removeIf(connection ->
                selectedIds.contains(connection.getOrigen())
                        && selectedIds.contains(connection.getDestino())
                        && !allowedPairs.contains(connectionKey(connection.getOrigen(), connection.getDestino())));
        int applied = 0;
        for (int i = 0; i < sequence.size() - 1; i++) {
            if (!context.hasConnection(sequence.get(i).getId(), sequence.get(i + 1).getId())) {
                addConnectionIfMissing(context, sequence.get(i).getId(), sequence.get(i + 1).getId());
                applied++;
            }
        }
        return applied;
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

    private void moveNodeRelative(ApplyContext context, Nodo node, Nodo reference, boolean before) {
        List<Conexion> nodeIncoming = context.connections.stream()
                .filter(connection -> node.getId().equals(connection.getDestino()))
                .toList();
        List<Conexion> nodeOutgoing = context.connections.stream()
                .filter(connection -> node.getId().equals(connection.getOrigen()))
                .toList();

        context.connections.removeIf(connection ->
                node.getId().equals(connection.getOrigen()) || node.getId().equals(connection.getDestino()));

        for (Conexion incoming : nodeIncoming) {
            for (Conexion outgoing : nodeOutgoing) {
                if (!Objects.equals(incoming.getOrigen(), outgoing.getDestino())
                        && !Objects.equals(incoming.getOrigen(), node.getId())
                        && !Objects.equals(outgoing.getDestino(), node.getId())) {
                    addConnectionIfMissing(context, incoming.getOrigen(), outgoing.getDestino());
                }
            }
        }

        if (before) {
            List<Conexion> incomingReference = context.connections.stream()
                    .filter(connection -> reference.getId().equals(connection.getDestino()))
                    .toList();
            context.connections.removeIf(connection -> reference.getId().equals(connection.getDestino()));
            for (Conexion incoming : incomingReference) {
                addConnectionIfMissing(context, incoming.getOrigen(), node.getId());
            }
            addConnectionIfMissing(context, node.getId(), reference.getId());
            node.setPosX(reference.getPosX() != null ? reference.getPosX() - 260 : node.getPosX());
            node.setPosY(reference.getPosY());
            return;
        }

        List<Conexion> outgoingReference = context.connections.stream()
                .filter(connection -> reference.getId().equals(connection.getOrigen()))
                .toList();
        context.connections.removeIf(connection -> reference.getId().equals(connection.getOrigen()));
        addConnectionIfMissing(context, reference.getId(), node.getId());
        for (Conexion outgoing : outgoingReference) {
            addConnectionIfMissing(context, node.getId(), outgoing.getDestino());
        }
        node.setPosX(reference.getPosX() != null ? reference.getPosX() + 260 : node.getPosX());
        node.setPosY(reference.getPosY());
    }

    private boolean applyExplicitPosition(Nodo node, WorkflowAiEditOperationDto operation) {
        Double x = firstDouble(operation, "x", "posX", "posicionX");
        Double y = firstDouble(operation, "y", "posY", "posicionY");
        Map<String, Object> position = firstMap(operation, "position", "posicion", "positionVisual", "visualPosition");
        if (position != null) {
            if (x == null) {
                x = asDouble(position.get("x"));
            }
            if (y == null) {
                y = asDouble(position.get("y"));
            }
        }

        boolean changed = false;
        if (x != null && !Objects.equals(node.getPosX(), x)) {
            node.setPosX(x);
            changed = true;
        }
        if (y != null && !Objects.equals(node.getPosY(), y)) {
            node.setPosY(y);
            changed = true;
        }
        return changed;
    }

    private boolean applyResponsibleToNode(
            Nodo node,
            WorkflowAiEditOperationDto operation,
            String prompt,
            ApplyContext context
    ) {
        if (node.getTipo() != TipoNodo.ACTIVIDAD) {
            return false;
        }

        if (firstBoolean(operation, "removeResponsible", "quitarResponsable")) {
            node.setResponsableTipo(null);
            node.setResponsableId(null);
            return true;
        }

        ResponsibleResolution responsible = resolveResponsible(operation, prompt);
        if (responsible == null) {
            return false;
        }

        if ("DEPARTAMENTO".equals(responsible.type())) {
            node.setDepartamentoId(responsible.id());
        }

        boolean changed = !Objects.equals(node.getResponsableTipo(), responsible.type())
                || !Objects.equals(node.getResponsableId(), responsible.id());
        node.setResponsableTipo(responsible.type());
        node.setResponsableId(responsible.id());
        return changed;
    }

    private ResponsibleResolution resolveResponsible(WorkflowAiEditOperationDto operation, String prompt) {
        String rawType = firstText(operation, "responsibleType", "responsableTipo", "responsibleTipo", "tipoResponsable");
        String normalizedType = normalizeForSearch(rawType);
        if (normalizedType.contains("initiator") || normalizedType.contains("iniciador") || normalizedType.contains("solicitante")) {
            return new ResponsibleResolution("USUARIO", RESPONSABLE_INICIADOR_TRAMITE_ID);
        }

        String explicitId = firstText(
                operation,
                "responsibleRoleId",
                "responsibleId",
                "responsableId",
                "departmentId",
                "departamentoId",
                "usuarioId",
                "userId"
        );
        if (explicitId != null) {
            if (normalizedType.contains("user") || normalizedType.contains("usuario") || usuarioRepository.existsById(explicitId)) {
                return new ResponsibleResolution("USUARIO", explicitId);
            }
            return new ResponsibleResolution("DEPARTAMENTO", explicitId);
        }

        String name = firstText(
                operation,
                "responsibleRoleName",
                "responsibleName",
                "responsableNombre",
                "departmentHint",
                "departmentName",
                "departamentoNombre",
                "usuarioNombre",
                "userName"
        );
        if (name == null) {
            name = extractDepartmentName(prompt);
        }
        if (name == null) {
            return null;
        }

        if (normalizedType.contains("user") || normalizedType.contains("usuario") || normalizedType.contains("funcionario")) {
            Optional<Usuario> user = resolveUserByName(name);
            return user.map(usuario -> new ResponsibleResolution("USUARIO", usuario.getId())).orElse(null);
        }

        Optional<Departamento> department = resolveDepartmentByName(name);
        if (department.isPresent()) {
            return new ResponsibleResolution("DEPARTAMENTO", department.get().getId());
        }

        Optional<Usuario> user = resolveUserByName(name);
        return user.map(usuario -> new ResponsibleResolution("USUARIO", usuario.getId())).orElse(null);
    }

    private Optional<Departamento> resolveDepartmentByName(String departmentName) {
        String cleanName = stripResponsiblePrefix(departmentName);
        Optional<Departamento> exact = departamentoRepository.findByNombreIgnoreCase(cleanName);
        if (exact.isPresent()) {
            return exact;
        }

        String normalizedNeedle = normalizeForSearch(cleanName);
        return departamentoRepository.findAll().stream()
                .filter(departamento -> {
                    String candidate = normalizeForSearch(departamento.getNombre());
                    return candidate.contains(normalizedNeedle) || normalizedNeedle.contains(candidate);
                })
                .findFirst();
    }

    private Optional<Usuario> resolveUserByName(String userName) {
        String cleanName = stripResponsiblePrefix(userName);
        String normalizedNeedle = normalizeForSearch(cleanName);
        return usuarioRepository.findAll().stream()
                .filter(usuario -> Boolean.TRUE.equals(usuario.getActivo()) || usuario.getActivo() == null)
                .filter(usuario -> {
                    String candidate = normalizeForSearch(usuario.getNombre());
                    return candidate.equals(normalizedNeedle)
                            || candidate.contains(normalizedNeedle)
                            || normalizedNeedle.contains(candidate);
                })
                .findFirst();
    }

    private String stripResponsiblePrefix(String value) {
        String text = normalize(value);
        if (text == null) {
            return "";
        }
        return text.replaceFirst("(?i)^(departamento|area|unidad|funcionario|usuario)\\s+", "").trim();
    }

    private Nodo resolveFormNode(ApplyContext context, WorkflowAiEditOperationDto operation, String prompt) {
        Nodo node = resolveOperationNode(context, operation, prompt);
        if (node != null) {
            return node;
        }
        String formNodeName = firstText(operation, "activityName", "activity", "formNodeName", "nodoFormulario");
        return context.findNode(null, formNodeName);
    }

    private List<CampoFormulario> ensureForm(Nodo node) {
        if (node.getFormulario() == null) {
            node.setFormulario(new ArrayList<>());
        } else if (!(node.getFormulario() instanceof ArrayList<?>)) {
            node.setFormulario(new ArrayList<>(node.getFormulario()));
        }
        return node.getFormulario();
    }

    private String extractFieldName(WorkflowAiEditOperationDto operation, String prompt) {
        String fieldName = firstText(
                operation,
                "fieldLabel",
                "fieldName",
                "field",
                "campo",
                "nombreCampo",
                "label"
        );
        if (fieldName != null) {
            return fieldName;
        }
        Map<String, Object> fieldMap = firstMap(operation, "field", "campo");
        if (fieldMap != null) {
            fieldName = normalize(asText(firstNonNull(fieldMap, "campo", "name", "label", "fieldLabel")));
            if (fieldName != null) {
                return fieldName;
            }
        }
        return extractFieldNameFromPrompt(prompt);
    }

    private String extractFieldNameFromPrompt(String prompt) {
        String text = normalize(prompt);
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile(
                "(?:campo)\\s+(?:tipo\\s+)?(?:texto|numero|n[uú]mero|fecha|archivo|booleano|checkbox)?\\s*(?:llamad[oa]\\s+)?[\"']?(.+?)[\"']?(?:\\s+(?:del|al|en\\s+el)\\s+formulario|$)",
                Pattern.CASE_INSENSITIVE
        ).matcher(text);
        if (matcher.find()) {
            return normalize(matcher.group(1));
        }
        return null;
    }

    private List<CampoFormulario> extractFormFields(WorkflowAiEditOperationDto operation) {
        Object fields = firstObject(operation, "fields", "campos", "formulario");
        if (!(fields instanceof Collection<?> collection)) {
            return List.of();
        }
        List<CampoFormulario> result = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                String name = normalize(asText(firstNonNull(map, "campo", "name", "label", "fieldLabel")));
                if (name == null) {
                    continue;
                }
                TipoCampo type = parseFieldType(asText(firstNonNull(map, "tipo", "type", "fieldType")), name);
                CampoFormulario field = CampoFormulario.builder()
                        .campo(name)
                        .tipo(type)
                        .etiqueta(normalize(asText(firstNonNull(map, "etiqueta", "label", "fieldLabel"))))
                        .requerido(asNullableBoolean(firstNonNull(map, "requerido", "required", "obligatorio")))
                        .placeholder(normalize(asText(firstNonNull(map, "placeholder"))))
                        .ayuda(normalize(asText(firstNonNull(map, "ayuda", "help", "description", "descripcion"))))
                        .orden(asInteger(firstNonNull(map, "orden", "order")))
                        .opciones(asStringList(firstNonNull(map, "opciones", "options")))
                        .validaciones(asStringObjectMap(firstNonNull(map, "validaciones", "validations")))
                        .build();
                if (field.getRequerido() == null) {
                    field.setRequerido(true);
                }
                result.add(field);
            }
        }
        return result;
    }

    private int findFieldIndex(List<CampoFormulario> form, String fieldName) {
        String normalized = normalizeForSearch(fieldName);
        for (int i = 0; i < form.size(); i++) {
            CampoFormulario field = form.get(i);
            if (field != null && normalizeForSearch(field.getCampo()).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private TipoCampo parseFieldType(String rawType, String fieldNameFallback) {
        String normalized = normalizeForSearch(rawType);
        String fallback = normalizeForSearch(fieldNameFallback);
        String source = (normalized + " " + fallback).trim();
        if (source.contains("colaborativo") || source.contains("collaborative")) {
            return TipoCampo.DOCUMENTO_COLABORATIVO;
        }
        if (source.contains("archivo") || source.contains("file") || source.contains("pdf") || source.contains("documento")) {
            return TipoCampo.ARCHIVO;
        }
        if (source.contains("checkbox") || source.contains("opcion multiple") || source.contains("casilla")) {
            return TipoCampo.CHECKBOX;
        }
        if (source.contains("seleccion") || source.contains("selection") || source.contains("dropdown") || source.contains("opcion unica")) {
            return TipoCampo.SELECCION;
        }
        if (source.contains("grid") || source.contains("matriz") || source.contains("tabla")) {
            return TipoCampo.GRID;
        }
        if (source.contains("label") || source.contains("etiqueta") || source.contains("mensaje") || source.contains("titulo")) {
            return TipoCampo.LABEL;
        }
        if (source.contains("boolean") || source.contains("si no") || source.contains("verdadero")) {
            return TipoCampo.BOOLEANO;
        }
        if (source.contains("numero") || source.contains("number") || source.contains("monto") || source.contains("cantidad")) {
            return TipoCampo.NUMERO;
        }
        if (source.contains("fecha") || source.contains("date")) {
            return TipoCampo.FECHA;
        }
        return TipoCampo.TEXTO;
    }

    private boolean applyFieldMetadata(CampoFormulario field, WorkflowAiEditOperationDto operation) {
        boolean changed = false;

        String label = firstText(operation, "newLabel", "label", "etiqueta");
        if (label != null && !Objects.equals(field.getEtiqueta(), label)) {
            field.setEtiqueta(label);
            changed = true;
        }

        Boolean required = firstNullableBoolean(operation, "required", "requerido", "obligatorio");
        if (required != null && !Objects.equals(field.getRequerido(), required)) {
            field.setRequerido(required);
            changed = true;
        }

        String placeholder = firstText(operation, "placeholder");
        if (placeholder != null && !Objects.equals(field.getPlaceholder(), placeholder)) {
            field.setPlaceholder(placeholder);
            changed = true;
        }

        String help = firstText(operation, "help", "ayuda", "description", "descripcion");
        if (help != null && !Objects.equals(field.getAyuda(), help)) {
            field.setAyuda(help);
            changed = true;
        }

        List<String> options = firstStringList(operation, "options", "opciones");
        if (!options.isEmpty() && !Objects.equals(field.getOpciones(), options)) {
            field.setOpciones(options);
            changed = true;
        }

        Map<String, Object> validations = firstMap(operation, "validations", "validaciones");
        if (validations != null && !Objects.equals(field.getValidaciones(), validations)) {
            field.setValidaciones(validations);
            changed = true;
        }

        return changed;
    }

    private void applyDecisionConditionForConnection(
            ApplyContext context,
            Conexion connection,
            WorkflowAiEditOperationDto operation
    ) {
        Nodo source = context.findNode(connection.getOrigen(), null);
        if (source == null || source.getTipo() != TipoNodo.DECISION) {
            return;
        }
        String condition = firstText(operation, "condition", "decisionCondition", "label", "transitionLabel", "resultado");
        if (condition == null) {
            condition = "ruta";
        }
        addOrUpdateDecisionCondition(source, condition, connection.getDestino(), null);
        touchNode(source);
    }

    private void applyDecisionOptions(ApplyContext context, Nodo node, WorkflowAiEditOperationDto operation) {
        if (node.getTipo() != TipoNodo.DECISION) {
            return;
        }
        List<String> options = firstStringList(operation, "options", "opciones");
        if (options.isEmpty()) {
            return;
        }
        context.warn("La decision " + node.getNombre()
                + " recibio opciones (" + String.join(", ", options)
                + "), pero se guardaran al conectar cada salida con su condicion.");
    }

    private void addOrUpdateDecisionCondition(Nodo decision, String condition, String targetId, String sourceActivityId) {
        if (decision.getCondiciones() == null) {
            decision.setCondiciones(new ArrayList<>());
        }
        for (CondicionDecision existing : decision.getCondiciones()) {
            if (existing != null && Objects.equals(existing.getSiguiente(), targetId)) {
                existing.setResultado(condition);
                return;
            }
        }
        decision.getCondiciones().add(CondicionDecision.builder()
                .resultado(condition)
                .siguiente(targetId)
                .origenActividadId(sourceActivityId)
                .grupo(GrupoCondicionDecision.builder()
                        .operadorLogico("AND")
                        .reglas(List.of(ReglaCondicionDecision.builder()
                                .campo("resultado")
                                .tipo("TEXTO")
                                .operador("IGUAL")
                                .valor(condition)
                                .build()))
                        .grupos(List.of())
                        .build())
                .build());
    }

    private void touchNode(Nodo node) {
        node.setFechaActualizacion(LocalDateTime.now());
        node.setVersion(node.getVersion() != null ? node.getVersion() + 1 : 1L);
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
        TipoNodo parsed = parseNodeTypeNullable(rawType);
        return parsed != null ? parsed : TipoNodo.ACTIVIDAD;
    }

    private TipoNodo parseNodeTypeNullable(String rawType) {
        String normalized = normalizeForSearch(rawType);
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.contains("decision")) {
            return TipoNodo.DECISION;
        }
        if (normalized.contains("fork") || normalized.contains("parallel start") || normalized.contains("parallel_start")) {
            return TipoNodo.FORK;
        }
        if (normalized.contains("join") || normalized.contains("parallel end") || normalized.contains("parallel_end")) {
            return TipoNodo.JOIN;
        }
        if (normalized.contains("fin") || normalized.contains("end")) {
            return TipoNodo.FIN;
        }
        if (normalized.contains("inicio") || normalized.contains("start")) {
            return TipoNodo.INICIO;
        }
        if (normalized.contains("actividad") || normalized.contains("task") || normalized.contains("tarea")) {
            return TipoNodo.ACTIVIDAD;
        }
        try {
            return TipoNodo.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeOperationType(String rawType) {
        String normalized = normalizeUpper(rawType);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "CREATE_NODE", "CREAR_NODO" -> "ADD_NODE";
            case "INSERT_BETWEEN", "INSERTAR_ENTRE" -> "ADD_NODE";
            case "CONNECT", "CONECTAR", "CREATE_EDGE" -> "ADD_TRANSITION";
            case "DISCONNECT", "DESCONECTAR", "DELETE_EDGE", "ELIMINAR_CONEXION" -> "DELETE_TRANSITION";
            case "CHANGE_RESPONSIBLE", "CAMBIAR_RESPONSABLE" -> "ASSIGN_RESPONSIBLE";
            case "QUITAR_RESPONSABLE", "REMOVE_ASSIGNEE" -> "REMOVE_RESPONSIBLE";
            case "ADD_FIELD", "AGREGAR_CAMPO_FORMULARIO" -> "ADD_FORM_FIELD";
            case "DELETE_FIELD", "ELIMINAR_CAMPO_FORMULARIO" -> "DELETE_FORM_FIELD";
            case "EDIT_FIELD", "EDITAR_CAMPO_FORMULARIO" -> "UPDATE_FORM";
            case "REORDER_SEQUENCE", "REORDENAR_FLUJO" -> "REORDER_FLOW";
            default -> normalized;
        };
    }

    private String connectionKey(String fromId, String toId) {
        return fromId + "->" + toId;
    }

    private boolean sameText(String left, String right) {
        return normalizeForSearch(left).equals(normalizeForSearch(right));
    }

    private boolean firstBoolean(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Object value = firstObject(operation, name);
            if (value instanceof Boolean bool) {
                return bool;
            }
            if (value instanceof String text) {
                String normalized = normalizeForSearch(text);
                if (normalized.equals("true") || normalized.equals("si") || normalized.equals("yes")) {
                    return true;
                }
            }
        }
        return false;
    }

    private Boolean firstNullableBoolean(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Boolean value = asNullableBoolean(firstObject(operation, name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Boolean asNullableBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            String normalized = normalizeForSearch(text);
            if (normalized.equals("true")
                    || normalized.equals("si")
                    || normalized.equals("yes")
                    || normalized.equals("obligatorio")
                    || normalized.equals("obligatoria")) {
                return true;
            }
            if (normalized.equals("false")
                    || normalized.equals("no")
                    || normalized.equals("opcional")
                    || normalized.equals("no obligatorio")
                    || normalized.equals("no obligatoria")) {
                return false;
            }
        }
        return null;
    }

    private boolean shouldAutoConnect(WorkflowAiEditOperationDto operation) {
        if (firstBoolean(operation, "skipAutoConnect", "noAutoConnect")) {
            return false;
        }
        Object explicit = firstObject(operation, "autoConnect", "connect");
        if (explicit instanceof Boolean bool) {
            return bool;
        }
        if (explicit instanceof String text) {
            String normalized = normalizeForSearch(text);
            if (normalized.equals("false") || normalized.equals("no") || normalized.equals("sin conectar")) {
                return false;
            }
        }
        return true;
    }

    private Double firstDouble(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Double value = asDouble(firstObject(operation, name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMap(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Object value = firstObject(operation, name);
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return result;
            }
        }
        return null;
    }

    private Object firstObject(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Object value = operationValue(operation, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private List<String> firstStringList(WorkflowAiEditOperationDto operation, String... names) {
        for (String name : names) {
            Object value = firstObject(operation, name);
            if (value instanceof Collection<?> collection) {
                List<String> result = collection.stream()
                        .map(this::asText)
                        .map(this::normalize)
                        .filter(Objects::nonNull)
                        .toList();
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return List.of();
    }

    private Object firstNonNull(Map<?, ?> map, String... names) {
        for (String name : names) {
            if (map.containsKey(name) && map.get(name) != null) {
                return map.get(name);
            }
        }
        return null;
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private Double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return List.of();
        }
        return collection.stream()
                .map(this::asText)
                .map(this::normalize)
                .filter(Objects::nonNull)
                .toList();
    }

    private Map<String, Object> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private Object operationValue(WorkflowAiEditOperationDto operation, String name) {
        String direct = directOperationValue(operation, name);
        if (direct != null) {
            return direct;
        }
        Object value = operation.property(name);
        if (value != null) {
            return value;
        }
        Object payload = operation.property("payload");
        if (payload instanceof Map<?, ?> map) {
            Object nested = firstNonNull(map, name);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private Map<String, Object> toIaContext(PoliticaNegocio politica) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("supportedNodeTypes", List.of("INICIO", "ACTIVIDAD", "DECISION", "FORK", "JOIN", "FIN"));
        context.put("supportedFieldTypes", List.of("TEXTO", "NUMERO", "BOOLEANO", "ARCHIVO", "FECHA"));
        context.put("departments", departamentoRepository.findAll().stream()
                .map(department -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", department.getId());
                    item.put("nombre", normalize(department.getNombre()) != null ? department.getNombre() : "");
                    return item;
                })
                .toList());
        context.put("users", usuarioRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getActivo()) || user.getActivo() == null)
                .map(user -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", user.getId());
                    item.put("nombre", user.getNombre());
                    item.put("rol", user.getRol());
                    item.put("departamentoId", user.getDepartamentoId());
                    return item;
                })
                .toList());
        context.put("dynamicResponsibles", List.of(
                Map.of("id", RESPONSABLE_INICIADOR_TRAMITE_ID, "nombre", "Iniciador del tramite", "type", "USUARIO"),
                Map.of("id", RESPONSABLE_USUARIO_FINAL_ID, "nombre", "Usuario final", "type", "USUARIO")
        ));
        Map<String, Object> currentPolicy = new LinkedHashMap<>();
        currentPolicy.put("id", politica.getId());
        currentPolicy.put("nombre", politica.getNombre());
        currentPolicy.put("estado", politica.getEstado() != null ? politica.getEstado().name() : "");
        context.put("currentPolicy", currentPolicy);
        return context;
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
            Object value = firstObject(operation, name);
            if (value == null || value instanceof Map<?, ?> || value instanceof Collection<?>) {
                continue;
            }
            String normalized = normalize(asText(value));
            if (normalized != null) {
                return normalized;
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
        private final String policyId;
        private final List<Nodo> nodes;
        private final List<Conexion> connections;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        private ApplyContext(PoliticaNegocio politica) {
            this.policyId = politica.getId();
            this.nodes = new ArrayList<>(optionalList(politica.getNodos()));
            this.connections = new ArrayList<>(optionalList(politica.getConexiones()));
        }

        private void warn(String message) {
            String normalized = normalize(message);
            if (normalized != null && !warnings.contains(normalized)) {
                warnings.add(normalized);
                log.warn("[WF-EDIT] policy={} {}", policyId, normalized);
            }
        }

        private long countByType(TipoNodo type) {
            return nodes.stream()
                    .filter(node -> node != null && node.getTipo() == type)
                    .count();
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

        private void removeBrokenConnections() {
            Set<String> nodeIds = new LinkedHashSet<>();
            for (Nodo node : nodes) {
                if (node != null && normalize(node.getId()) != null) {
                    nodeIds.add(node.getId());
                }
            }

            int before = connections.size();
            connections.removeIf(connection -> connection == null
                    || !nodeIds.contains(connection.getOrigen())
                    || !nodeIds.contains(connection.getDestino()));
            int removed = before - connections.size();
            if (removed > 0) {
                warn("Se quitaron " + removed + " conexiones invalidas generadas por la edicion IA.");
            }

            Set<String> seenConnections = new LinkedHashSet<>();
            int beforeDeduplicate = connections.size();
            connections.removeIf(connection -> !seenConnections.add(connectionKey(connection.getOrigen(), connection.getDestino())));
            int duplicates = beforeDeduplicate - connections.size();
            if (duplicates > 0) {
                warn("Se quitaron " + duplicates + " conexiones duplicadas generadas por la edicion IA.");
            }
        }

        private String findBlockingGraphError() {
            if (nodes.stream().noneMatch(node -> node != null && node.getTipo() == TipoNodo.INICIO)) {
                return "La edicion IA dejaria la politica sin nodo INICIO.";
            }
            if (nodes.stream().noneMatch(node -> node != null && node.getTipo() == TipoNodo.FIN)) {
                return "La edicion IA dejaria la politica sin nodo FIN.";
            }

            Set<String> seenIds = new LinkedHashSet<>();
            for (Nodo node : nodes) {
                if (node == null) {
                    return "La edicion IA produjo un nodo nulo.";
                }
                String id = normalize(node.getId());
                if (id == null) {
                    return "La edicion IA produjo un nodo sin ID.";
                }
                if (!seenIds.add(id)) {
                    return "La edicion IA produjo IDs duplicados de nodo: " + id + ".";
                }
            }

            for (Nodo node : nodes) {
                if (node.getTipo() == TipoNodo.ACTIVIDAD) {
                    String duplicateField = findDuplicateField(node);
                    if (duplicateField != null) {
                        return "La actividad " + node.getNombre() + " quedaria con campo duplicado: " + duplicateField + ".";
                    }
                }
            }
            return null;
        }

        private String findDuplicateField(Nodo node) {
            if (node.getFormulario() == null) {
                return null;
            }
            Set<String> seen = new LinkedHashSet<>();
            for (CampoFormulario field : node.getFormulario()) {
                if (field == null) {
                    continue;
                }
                String normalized = normalizeForSearch(field.getCampo());
                if (normalized.isEmpty()) {
                    continue;
                }
                if (!seen.add(normalized)) {
                    return field.getCampo();
                }
            }
            return null;
        }
    }
}
