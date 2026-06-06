package com.leo.politicas_de_negocio.iaeditorflujo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.iaeditorflujo.client.WorkflowAiEditorClient;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditApplyResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditOperationDto;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditPreviewResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditProposalResponse;
import com.leo.politicas_de_negocio.iaeditorflujo.validator.WorkflowAiEditValidator;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowAiEditorServiceTest {

    @Mock
    private WorkflowAiEditorClient workflowAiEditorClient;

    @Mock
    private PoliticaNegocioRepository politicaNegocioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DepartamentoRepository departamentoRepository;

    private AutoCloseable mocks;
    private WorkflowAiEditorService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new WorkflowAiEditorService(
                workflowAiEditorClient,
                politicaNegocioRepository,
                usuarioRepository,
                departamentoRepository,
                new WorkflowAiEditValidator(),
                new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void previewEdition_debeRetornarPreviewValidaCuandoOperacionReferenciaNodosExistentes() {
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(policy()));
        when(workflowAiEditorClient.previewEdition(any())).thenReturn(successProposal("Validar datos", "Solicitar datos del paciente"));

        WorkflowAiEditPreviewResponse response = service.previewEdition("admin-1", "pol-1", previewRequest("Crear un bucle"));

        assertTrue(response.isSuccess());
        assertTrue(response.isValid());
        assertEquals("UPDATE_WORKFLOW", response.getIntent());
        assertEquals(1, response.getOperations().size());
        verify(workflowAiEditorClient).previewEdition(any());
    }

    @Test
    void previewEdition_debeMarcarPreviewInvalidaSiOperacionReferenciaNodoInexistente() {
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(policy()));
        when(workflowAiEditorClient.previewEdition(any())).thenReturn(successProposal("Nodo fantasma", "Solicitar datos del paciente"));

        WorkflowAiEditPreviewResponse response = service.previewEdition("admin-1", "pol-1", previewRequest("Crear un bucle"));

        assertTrue(!response.isValid());
        assertTrue(response.getErrors().stream().anyMatch(message -> message.contains("Nodo fantasma")));
    }

    @Test
    void previewEdition_debePermitirInsertarNodoNuevoEntreDosNodosExistentes() {
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(policy()));
        when(workflowAiEditorClient.previewEdition(any())).thenReturn(insertBetweenProposal());

        WorkflowAiEditPreviewResponse response = service.previewEdition(
                "admin-1",
                "pol-1",
                previewRequest("Agregar un nodo de actividad 'Pedir foto' entre 'Solicitar datos' y 'Validar datos'")
        );

        assertTrue(response.isSuccess());
        assertTrue(response.isValid());
        assertEquals(4, response.getOperations().size());
        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    void applyEdition_debeAplicarYGuardarNodoNuevoDesdePrompt() {
        PoliticaNegocio politica = policy();
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workflowAiEditorClient.previewEdition(any())).thenReturn(invalidAddNodeProposal());

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("añadime el nodo pedir foto");

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        assertEquals("Cambios aplicados y guardados en la politica.", response.getMessage());
        assertTrue(politica.getNodos().stream().anyMatch(node -> "Pedir Foto".equals(node.getNombre())));
        verify(politicaNegocioRepository).save(any());
    }

    @Test
    void applyEdition_debeActualizarResponsablePorNombreDeDepartamento() {
        PoliticaNegocio politica = policy();
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(departamentoRepository.findByNombreIgnoreCase("Admisión"))
                .thenReturn(Optional.of(Departamento.builder().id("dep-admision").nombre("Admisión").build()));
        when(workflowAiEditorClient.previewEdition(any())).thenReturn(updateResponsibleProposal());

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("cambiame el responsable del nodo solicitar datos del paciente y pone Admisión");

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        assertEquals("DEPARTAMENTO", politica.getNodos().get(1).getResponsableTipo());
        assertEquals("dep-admision", politica.getNodos().get(1).getResponsableId());
    }

    @Test
    void applyEdition_debeAgregarCampoFormularioDinamicoEnActividad() {
        PoliticaNegocio politica = policy();
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowAiEditOperationDto operation = operationWithType("ADD_FORM_FIELD");
        operation.setNodeName("Validar datos");
        operation.addProperty("fieldLabel", "Comprobante de pago");
        operation.addProperty("fieldType", "ARCHIVO");
        operation.addProperty("required", false);
        operation.addProperty("placeholder", "Adjunta el comprobante en PDF");

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("Agrega comprobante de pago al formulario de Validar datos");
        request.setOperations(List.of(operation));

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        Nodo validar = politica.getNodos().get(2);
        assertTrue(validar.getFormulario().stream().anyMatch(field ->
                "Comprobante de pago".equals(field.getCampo())
                        && field.getTipo() == TipoCampo.ARCHIVO
                        && Boolean.FALSE.equals(field.getRequerido())
                        && "Adjunta el comprobante en PDF".equals(field.getPlaceholder())));
    }

    @Test
    void applyEdition_debeEditarCampoFormularioExistente() {
        PoliticaNegocio politica = policy();
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowAiEditOperationDto operation = operationWithType("UPDATE_FORM");
        operation.setNodeName("Validar datos");
        operation.addProperty("fieldLabel", "Observacion");
        operation.addProperty("newName", "Observaciones finales");
        operation.addProperty("fieldType", "TEXTO");

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("Modifica el campo Observacion del formulario de Validar datos");
        request.setOperations(List.of(operation));

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        Nodo validar = politica.getNodos().get(2);
        assertEquals("Observaciones finales", validar.getFormulario().get(0).getCampo());
        assertEquals(TipoCampo.TEXTO, validar.getFormulario().get(0).getTipo());
    }

    private WorkflowAiEditPreviewRequest previewRequest(String prompt) {
        WorkflowAiEditPreviewRequest request = new WorkflowAiEditPreviewRequest();
        request.setPrompt(prompt);
        return request;
    }

    private WorkflowAiEditProposalResponse successProposal(String fromNodeName, String toNodeName) {
        WorkflowAiEditProposalResponse response = new WorkflowAiEditProposalResponse();
        response.setSuccess(true);
        response.setIntent("UPDATE_WORKFLOW");
        response.setSummary("Se propone conectar nodos.");
        response.setOperations(List.of(operation("CREATE_LOOP", fromNodeName, toNodeName)));
        response.setWarnings(List.of());
        response.setErrors(List.of());
        response.setRequiresConfirmation(true);
        return response;
    }

    private WorkflowAiEditProposalResponse insertBetweenProposal() {
        WorkflowAiEditProposalResponse response = new WorkflowAiEditProposalResponse();
        response.setSuccess(true);
        response.setIntent("UPDATE_WORKFLOW");
        response.setSummary("Se propone insertar el nodo Pedir foto entre Solicitar datos y Validar datos.");
        response.setOperations(List.of(
                addNodeOperation("Pedir foto"),
                operation("DELETE_TRANSITION", "Solicitar datos del paciente", "Validar datos"),
                operation("ADD_TRANSITION", "Solicitar datos del paciente", "Pedir foto"),
                operation("ADD_TRANSITION", "Pedir foto", "Validar datos")
        ));
        response.setWarnings(List.of());
        response.setErrors(List.of());
        response.setRequiresConfirmation(true);
        return response;
    }

    private WorkflowAiEditProposalResponse invalidAddNodeProposal() {
        WorkflowAiEditProposalResponse response = new WorkflowAiEditProposalResponse();
        response.setSuccess(true);
        response.setIntent("UPDATE_WORKFLOW");
        response.setSummary("Agregar nodo 'Pedir foto' entre 'Solicitar datos del paciente' y 'Validar datos'");
        response.setOperations(List.of(
                operationWithType("ADD_NODE"),
                operationWithType("DELETE_TRANSITION"),
                operationWithType("ADD_TRANSITION"),
                operationWithType("ADD_TRANSITION")
        ));
        response.setWarnings(List.of());
        response.setErrors(List.of());
        response.setRequiresConfirmation(false);
        return response;
    }

    private WorkflowAiEditProposalResponse updateResponsibleProposal() {
        WorkflowAiEditProposalResponse response = new WorkflowAiEditProposalResponse();
        response.setSuccess(true);
        response.setIntent("UPDATE_WORKFLOW");
        WorkflowAiEditOperationDto operation = operationWithType("UPDATE_NODE");
        operation.setNodeName("Solicitar datos del paciente");
        operation.addProperty("departmentHint", "Admisión");
        response.setOperations(List.of(operation));
        response.setWarnings(List.of());
        response.setErrors(List.of());
        response.setRequiresConfirmation(false);
        return response;
    }

    private WorkflowAiEditOperationDto operation(String type, String fromNodeName, String toNodeName) {
        WorkflowAiEditOperationDto operation = new WorkflowAiEditOperationDto();
        operation.setType(type);
        operation.setFromNodeName(fromNodeName);
        operation.setToNodeName(toNodeName);
        return operation;
    }

    private WorkflowAiEditOperationDto operationWithType(String type) {
        WorkflowAiEditOperationDto operation = new WorkflowAiEditOperationDto();
        operation.setType(type);
        return operation;
    }

    private WorkflowAiEditOperationDto addNodeOperation(String nodeName) {
        WorkflowAiEditOperationDto operation = new WorkflowAiEditOperationDto();
        operation.setType("ADD_NODE");
        operation.addProperty("name", nodeName);
        return operation;
    }

    private PoliticaNegocio policy() {
        return PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Politica de prueba")
                .nodos(List.of(
                        Nodo.builder().id("n1").tipo(TipoNodo.INICIO).nombre("Inicio").build(),
                        Nodo.builder().id("n2").tipo(TipoNodo.ACTIVIDAD).nombre("Solicitar datos del paciente").build(),
                        Nodo.builder()
                                .id("n3")
                                .tipo(TipoNodo.ACTIVIDAD)
                                .nombre("Validar datos")
                                .formulario(List.of(CampoFormulario.builder()
                                        .campo("Observacion")
                                        .tipo(TipoCampo.TEXTO)
                                        .build()))
                                .build(),
                        Nodo.builder().id("n4").tipo(TipoNodo.FIN).nombre("Fin").build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("n1").destino("n2").build(),
                        Conexion.builder().origen("n2").destino("n3").build(),
                        Conexion.builder().origen("n3").destino("n4").build()
                ))
                .build();
    }

    private Usuario admin() {
        return Usuario.builder()
                .id("admin-1")
                .rol("ADMIN")
                .build();
    }

    @Test
    void applyEdition_debeAgregarRequisitoInicial() {
        PoliticaNegocio politica = policy();
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowAiEditOperationDto operation = operationWithType("ADD_INITIAL_REQUIREMENT");
        operation.addProperty("fieldLabel", "Código de cliente");
        operation.addProperty("fieldType", "TEXTO");
        operation.addProperty("required", true);

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("Agrega el codigo de cliente como requisito inicial");
        request.setOperations(List.of(operation));

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        assertTrue(politica.getRequisitosIniciales().stream().anyMatch(req ->
                "Código de cliente".equals(req.getCampo())
                        && req.getTipo() == TipoCampo.TEXTO
                        && Boolean.TRUE.equals(req.getRequerido())));
    }

    @Test
    void applyEdition_debeEliminarRequisitoInicial() {
        PoliticaNegocio politica = policy();
        politica.setRequisitosIniciales(List.of(CampoFormulario.builder()
                .campo("Código de cliente")
                .tipo(TipoCampo.TEXTO)
                .build()));
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowAiEditOperationDto operation = operationWithType("DELETE_INITIAL_REQUIREMENT");
        operation.addProperty("fieldLabel", "Código de cliente");

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("Elimina el codigo de cliente de los requisitos iniciales");
        request.setOperations(List.of(operation));

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        assertTrue(politica.getRequisitosIniciales().isEmpty());
    }

    @Test
    void applyEdition_debeEditarRequisitoInicial() {
        PoliticaNegocio politica = policy();
        politica.setRequisitosIniciales(List.of(CampoFormulario.builder()
                .campo("Código de cliente")
                .tipo(TipoCampo.TEXTO)
                .build()));
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaNegocioRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaNegocioRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkflowAiEditOperationDto operation = operationWithType("UPDATE_INITIAL_REQUIREMENT");
        operation.addProperty("fieldLabel", "Código de cliente");
        operation.addProperty("newName", "Código de usuario");
        operation.addProperty("fieldType", "NUMERO");

        WorkflowAiEditApplyRequest request = new WorkflowAiEditApplyRequest();
        request.setPrompt("Cambia el requisito inicial Código de cliente a Código de usuario y que sea numero");
        request.setOperations(List.of(operation));

        WorkflowAiEditApplyResponse response = service.applyEdition("admin-1", "pol-1", request);

        assertTrue(response.isSuccess());
        assertEquals("Código de usuario", politica.getRequisitosIniciales().get(0).getCampo());
        assertEquals(TipoCampo.NUMERO, politica.getRequisitosIniciales().get(0).getTipo());
    }
}

