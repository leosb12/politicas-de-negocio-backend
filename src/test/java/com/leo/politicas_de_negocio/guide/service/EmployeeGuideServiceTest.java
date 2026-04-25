package com.leo.politicas_de_negocio.guide.service;

import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.guide.client.GuideIaClient;
import com.leo.politicas_de_negocio.guide.dto.EmployeeGuideIaRequest;
import com.leo.politicas_de_negocio.guide.dto.EmployeeGuideRequest;
import com.leo.politicas_de_negocio.guide.dto.EmployeeGuideResponse;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.dto.SeguimientoInstanciaResponse;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.CondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.tareas.dto.TareaDetalleResponse;
import com.leo.politicas_de_negocio.tareas.dto.TareaMiaResponse;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.service.TareaActividadService;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmployeeGuideServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TareaActividadService tareaActividadService;

    @Mock
    private InstanciaPoliticaService instanciaPoliticaService;

    @Mock
    private PoliticaNegocioRepository politicaNegocioRepository;

    @Mock
    private DepartamentoRepository departamentoRepository;

    @Mock
    private GuideIaClient guideIaClient;

    private AutoCloseable mocks;
    private EmployeeGuideService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new EmployeeGuideService(
                usuarioRepository,
                tareaActividadService,
                instanciaPoliticaService,
                politicaNegocioRepository,
                departamentoRepository,
                guideIaClient,
                new EmployeeGuideFallbackService(),
                new EmployeeGuideIntentResolver()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void guideEmployee_debeArmarContextoYDelegarALaIa() {
        when(usuarioRepository.findByIdAndActivo("func-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("func-1").rol("FUNCIONARIO").nombre("Funcionario Uno").build()));
        when(tareaActividadService.listarMisTareasResumen("func-1"))
                .thenReturn(List.of(buildTaskSummary()));
        when(tareaActividadService.obtenerDetalleTarea("func-1", "task-1"))
                .thenReturn(buildTaskDetailWithMissingField());
        when(instanciaPoliticaService.obtenerSeguimientoPorId("func-1", "inst-1"))
                .thenReturn(buildTracking());
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy()));
        when(departamentoRepository.findById("dep-1"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder()
                        .id("dep-1")
                        .nombre("Departamento Tecnico")
                        .build()));
        when(departamentoRepository.findById("dep-2"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder()
                        .id("dep-2")
                        .nombre("Departamento Legal")
                        .build()));
        when(guideIaClient.guideEmployee(any(EmployeeGuideIaRequest.class)))
                .thenReturn(EmployeeGuideResponse.builder()
                        .answer("Respuesta IA funcionario")
                        .source("AI")
                        .available(true)
                        .build());

        EmployeeGuideRequest request = EmployeeGuideRequest.builder()
                .screen("TASK_FORM")
                .question("Que pasa despues?")
                .context(com.leo.politicas_de_negocio.guide.dto.EmployeeGuideContextRequest.builder()
                        .taskId("task-1")
                        .availableActions(List.of("START_TASK", "SAVE_FORM"))
                        .build())
                .build();

        EmployeeGuideResponse response = service.guideEmployee("func-1", request);

        assertEquals("Respuesta IA funcionario", response.getAnswer());

        ArgumentCaptor<EmployeeGuideIaRequest> captor = ArgumentCaptor.forClass(EmployeeGuideIaRequest.class);
        verify(guideIaClient).guideEmployee(captor.capture());
        EmployeeGuideIaRequest iaRequest = captor.getValue();
        assertEquals("func-1", iaRequest.getUserId());
        assertEquals("EMPLOYEE", iaRequest.getRole());
        assertEquals("TASK_FORM", iaRequest.getScreen());
        assertEquals("Instalacion de medidor", iaRequest.getContext().getPolicyName());
        assertEquals("Evaluar viabilidad tecnica", iaRequest.getContext().getCurrentNode().getName());
        assertTrue(iaRequest.getContext().getForm().getMissingRequiredFields().contains("viable"));
        assertTrue(iaRequest.getContext().getAvailableActions().contains("COMPLETE_TASK"));
        assertEquals("HIGH", iaRequest.getContext().getPriority());
        assertTrue(iaRequest.getContext().getNextPossibleSteps().stream()
                .anyMatch(step -> "Revision legal".equals(step.getNextNode())));
    }

    @Test
    void guideEmployee_debeUsarFallbackSiIaNoResponde() {
        when(usuarioRepository.findByIdAndActivo("func-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("func-1").rol("FUNCIONARIO").nombre("Funcionario Uno").build()));
        when(tareaActividadService.listarMisTareasResumen("func-1"))
                .thenReturn(List.of(buildTaskSummary()));
        when(tareaActividadService.obtenerDetalleTarea("func-1", "task-1"))
                .thenReturn(buildTaskDetailWithMissingField());
        when(instanciaPoliticaService.obtenerSeguimientoPorId("func-1", "inst-1"))
                .thenReturn(buildTracking());
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy()));
        when(departamentoRepository.findById("dep-1"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder()
                        .id("dep-1")
                        .nombre("Departamento Tecnico")
                        .build()));
        when(departamentoRepository.findById("dep-2"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder()
                        .id("dep-2")
                        .nombre("Departamento Legal")
                        .build()));
        when(guideIaClient.guideEmployee(any(EmployeeGuideIaRequest.class))).thenReturn(null);

        EmployeeGuideRequest request = EmployeeGuideRequest.builder()
                .screen("TASK_FORM")
                .question("Por que no puedo finalizar?")
                .context(com.leo.politicas_de_negocio.guide.dto.EmployeeGuideContextRequest.builder()
                        .taskId("task-1")
                        .availableActions(List.of("SAVE_FORM", "COMPLETE_TASK"))
                        .build())
                .build();

        EmployeeGuideResponse response = service.guideEmployee("func-1", request);

        assertEquals("BACKEND_FALLBACK", response.getSource());
        assertEquals("ERROR", response.getSeverity());
        assertTrue(response.getMissingFields().stream().anyMatch(item -> "viable".equals(item.getField())));
        assertTrue(response.getSuggestedActions().stream().anyMatch(item -> "COMPLETE_REQUIRED_FIELDS".equals(item.getAction())));
    }

    private TareaMiaResponse buildTaskSummary() {
        return TareaMiaResponse.builder()
                .id("task-1")
                .nombreActividad("Evaluar viabilidad tecnica")
                .estadoTarea(EstadoTarea.EN_PROCESO)
                .instanciaId("inst-1")
                .politicaId("pol-1")
                .politicaNombre("Instalacion de medidor")
                .fechaCreacion(LocalDateTime.now().minusHours(52))
                .fechaInicio(LocalDateTime.now().minusHours(4))
                .prioridad("ALTA")
                .responsableTipo("DEPARTAMENTO")
                .responsableId("dep-1")
                .responsableActual("dep-1")
                .codigoTramite("TRM-001")
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .contextoResumen(Map.of("solicitante", "Cliente Uno"))
                .build();
    }

    private TareaDetalleResponse buildTaskDetailWithMissingField() {
        return TareaDetalleResponse.builder()
                .id("task-1")
                .estadoTarea(EstadoTarea.EN_PROCESO)
                .fechaCreacion(LocalDateTime.now().minusHours(52))
                .fechaInicio(LocalDateTime.now().minusHours(4))
                .asignadoA("func-1")
                .asignadoANombre("Funcionario Uno")
                .observaciones("Pendiente de validar campo principal")
                .actividad(TareaDetalleResponse.ActividadTareaResponse.builder()
                        .nodoId("node-1")
                        .nombreActividad("Evaluar viabilidad tecnica")
                        .responsableTipo("DEPARTAMENTO")
                        .responsableId("dep-1")
                        .formularioDefinicion(List.of(
                                CampoFormulario.builder().campo("viable").tipo(TipoCampo.BOOLEANO).build(),
                                CampoFormulario.builder().campo("observaciones").tipo(TipoCampo.TEXTO).build()
                        ))
                        .build())
                .formularioRespuesta(Map.of("observaciones", "Se reviso documentacion"))
                .instancia(InstanciaDetalleResponse.builder()
                        .id("inst-1")
                        .politicaId("pol-1")
                        .politicaNombre("Instalacion de medidor")
                        .estadoInstancia(EstadoInstancia.EN_CURSO)
                        .codigoTramite("TRM-001")
                        .build())
                .politica(TareaDetalleResponse.PoliticaResumenResponse.builder()
                        .id("pol-1")
                        .nombre("Instalacion de medidor")
                        .descripcion("Politica operativa")
                        .estado(EstadoPolitica.ACTIVA)
                        .build())
                .historialRelevante(List.of())
                .build();
    }

    private SeguimientoInstanciaResponse buildTracking() {
        return SeguimientoInstanciaResponse.builder()
                .instanciaId("inst-1")
                .politicaId("pol-1")
                .politicaNombre("Instalacion de medidor")
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .tareas(List.of(
                        SeguimientoInstanciaResponse.TareaSeguimientoResponse.builder()
                                .id("task-0")
                                .nodoId("node-prev")
                                .nombreNodo("Recepcion de solicitud")
                                .responsableNombre("Atencion al Cliente")
                                .estadoTarea(EstadoTarea.COMPLETADA)
                                .fechaFin(LocalDateTime.now().minusHours(6))
                                .build(),
                        SeguimientoInstanciaResponse.TareaSeguimientoResponse.builder()
                                .id("task-1")
                                .nodoId("node-1")
                                .nombreNodo("Evaluar viabilidad tecnica")
                                .responsableNombre("Departamento Tecnico")
                                .estadoTarea(EstadoTarea.EN_PROCESO)
                                .fechaInicio(LocalDateTime.now().minusHours(4))
                                .build()
                ))
                .departamentosActuales(List.of(
                        SeguimientoInstanciaResponse.DepartamentoActualResponse.builder()
                                .departamentoId("dep-1")
                                .departamentoNombre("Departamento Tecnico")
                                .nodoId("node-1")
                                .nodoNombre("Evaluar viabilidad tecnica")
                                .tareaId("task-1")
                                .estadoTarea(EstadoTarea.EN_PROCESO)
                                .build()
                ))
                .nodosActualesIds(List.of("node-1"))
                .build();
    }

    private PoliticaNegocio buildPolicy() {
        return PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Instalacion de medidor")
                .estado(EstadoPolitica.ACTIVA)
                .nodos(List.of(
                        Nodo.builder().id("node-1").nombre("Evaluar viabilidad tecnica").tipo(TipoNodo.ACTIVIDAD).departamentoId("dep-1")
                                .formulario(List.of(
                                        CampoFormulario.builder().campo("viable").tipo(TipoCampo.BOOLEANO).build(),
                                        CampoFormulario.builder().campo("observaciones").tipo(TipoCampo.TEXTO).build()
                                ))
                                .build(),
                        Nodo.builder().id("decision-1").nombre("Decision tecnica").tipo(TipoNodo.DECISION)
                                .condiciones(List.of(
                                        CondicionDecision.builder().resultado("Si").siguiente("node-legal").build(),
                                        CondicionDecision.builder().resultado("No").siguiente("node-reject").build()
                                ))
                                .build(),
                        Nodo.builder().id("node-legal").nombre("Revision legal").tipo(TipoNodo.ACTIVIDAD).departamentoId("dep-2").build(),
                        Nodo.builder().id("node-reject").nombre("Rechazo de solicitud").tipo(TipoNodo.ACTIVIDAD).departamentoId("dep-1").build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("node-1").destino("decision-1").build(),
                        Conexion.builder().origen("decision-1").destino("node-legal").build(),
                        Conexion.builder().origen("decision-1").destino("node-reject").build()
                ))
                .build();
    }
}
