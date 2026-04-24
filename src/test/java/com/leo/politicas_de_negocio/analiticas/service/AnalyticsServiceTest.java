package com.leo.politicas_de_negocio.analiticas.service;

import com.leo.politicas_de_negocio.analiticas.client.AnalyticsIaClient;
import com.leo.politicas_de_negocio.analiticas.dto.response.AttentionTimesAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Mock
    private PoliticaNegocioRepository politicaRepository;
    @Mock
    private InstanciaPoliticaRepository instanciaRepository;
    @Mock
    private TareaActividadRepository tareaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private AnalyticsIaClient analyticsIaClient;

    private AutoCloseable mocks;
    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AnalyticsService(
                politicaRepository,
                instanciaRepository,
                tareaRepository,
                usuarioRepository,
                departamentoRepository,
                new AnalyticsMapper(),
                analyticsIaClient
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void getGeneralMetrics_debeCalcularResumenYTiempoPromedio() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaRepository.findAll()).thenReturn(List.of(
                PoliticaNegocio.builder().id("pol-1").estado(EstadoPolitica.ACTIVA).build(),
                PoliticaNegocio.builder().id("pol-2").estado(EstadoPolitica.DESHABILITADA).build()
        ));
        when(instanciaRepository.findAll()).thenReturn(List.of(
                InstanciaPolitica.builder()
                        .id("inst-1")
                        .politicaId("pol-1")
                        .estadoInstancia(EstadoInstancia.FINALIZADA)
                        .fechaCreacion(LocalDateTime.of(2026, 4, 20, 8, 0))
                        .fechaFinalizacion(LocalDateTime.of(2026, 4, 21, 8, 0))
                        .build(),
                InstanciaPolitica.builder().id("inst-2").estadoInstancia(EstadoInstancia.EN_CURSO).build(),
                InstanciaPolitica.builder().id("inst-3").estadoInstancia(EstadoInstancia.CANCELADA).build()
        ));
        when(tareaRepository.findAll()).thenReturn(List.of(
                TareaActividad.builder().id("t-1").estadoTarea(EstadoTarea.PENDIENTE).build(),
                TareaActividad.builder().id("t-2").estadoTarea(EstadoTarea.EN_PROCESO).build(),
                TareaActividad.builder().id("t-3").estadoTarea(EstadoTarea.COMPLETADA).build()
        ));

        GeneralAnalyticsResponse response = service.getGeneralMetrics("admin-1");

        assertEquals(2L, response.getTotalPolicies());
        assertEquals(1L, response.getActivePolicies());
        assertEquals(3L, response.getTotalInstances());
        assertEquals(1L, response.getCompletedInstances());
        assertEquals(1L, response.getRejectedInstances());
        assertEquals(1L, response.getInProgressInstances());
        assertEquals(2L, response.getPendingTasks());
        assertEquals(1L, response.getCompletedTasks());
        assertEquals(24.0d, response.getAverageResolutionTimeHours());
        assertTrue(response.isHasEnoughResolutionTimeData());
    }

    @Test
    void getAttentionTimes_debeRetornarVacioSiNoHayFechasSuficientes() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaRepository.findAll()).thenReturn(List.of(
                PoliticaNegocio.builder().id("pol-1").nombre("Credito").build()
        ));
        when(instanciaRepository.findAll()).thenReturn(List.of(
                InstanciaPolitica.builder().id("inst-1").politicaId("pol-1").estadoInstancia(EstadoInstancia.FINALIZADA).build()
        ));
        when(tareaRepository.findAll()).thenReturn(List.of(
                TareaActividad.builder().id("t-1").politicaId("pol-1").nodoId("n-1").nombreNodo("Revision").estadoTarea(EstadoTarea.COMPLETADA).build()
        ));
        when(departamentoRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findAll()).thenReturn(List.of());

        AttentionTimesAnalyticsResponse response = service.getAttentionTimes("admin-1");

        assertTrue(response.getAverageByPolicy().isEmpty());
        assertTrue(response.getAverageByNode().isEmpty());
        assertTrue(response.getAverageByDepartment().isEmpty());
        assertTrue(response.getAverageByOfficial().isEmpty());
        assertFalse(response.isHasEnoughData());
    }

    @Test
    void getTaskAccumulation_debeAgruparPorFuncionarioDepartamentoPoliticaYNodo() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(usuarioRepository.findAll()).thenReturn(List.of(
                Usuario.builder().id("func-1").nombre("Juan Perez").departamentoId("dep-1").build()
        ));
        when(departamentoRepository.findAll()).thenReturn(List.of(
                Departamento.builder().id("dep-1").nombre("Legal").build()
        ));
        when(politicaRepository.findAll()).thenReturn(List.of(
                PoliticaNegocio.builder()
                        .id("pol-1")
                        .nombre("Solicitud de Credito")
                        .nodos(List.of(Nodo.builder().id("n-1").nombre("Revision documental").departamentoId("dep-1").build()))
                        .build()
        ));
        when(tareaRepository.findAll()).thenReturn(List.of(
                TareaActividad.builder()
                        .id("t-1")
                        .politicaId("pol-1")
                        .nodoId("n-1")
                        .nombreNodo("Revision documental")
                        .estadoTarea(EstadoTarea.PENDIENTE)
                        .fechaCreacion(LocalDateTime.now().minusHours(30))
                        .asignadoA("func-1")
                        .build()
        ));

        TaskAccumulationAnalyticsResponse response = service.getTaskAccumulation("admin-1");

        assertEquals(1, response.getPendingByOfficial().size());
        assertEquals("Juan Perez", response.getPendingByOfficial().get(0).getOfficialName());
        assertEquals(1, response.getPendingByDepartment().size());
        assertEquals("Legal", response.getPendingByDepartment().get(0).getDepartmentName());
        assertEquals(1, response.getPendingByPolicy().size());
        assertEquals("Solicitud de Credito", response.getPendingByPolicy().get(0).getPolicyName());
        assertEquals(1, response.getPendingByNode().size());
        assertEquals("Revision documental", response.getPendingByNode().get(0).getNodeName());
        assertEquals(1, response.getOldestPendingTasks().size());
    }

    @Test
    void getGeneralMetrics_debeBloquearSiUsuarioNoEsAdmin() {
        when(usuarioRepository.findByIdAndActivo("user-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("user-1").rol("FUNCIONARIO").activo(true).build()));

        ApiException ex = assertThrows(ApiException.class, () -> service.getGeneralMetrics("user-1"));

        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void getBottlenecks_debeReutilizarDashboardYDelegarEnClienteIa() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaRepository.findAll()).thenReturn(List.of());
        when(instanciaRepository.findAll()).thenReturn(List.of());
        when(tareaRepository.findAll()).thenReturn(List.of());
        when(departamentoRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(analyticsIaClient.analyzeBottlenecks(org.mockito.ArgumentMatchers.any()))
                .thenReturn(BottlenecksAnalyticsResponse.builder()
                        .summary("ok")
                        .bottlenecks(List.of())
                        .source("AI")
                        .available(true)
                        .build());

        BottlenecksAnalyticsResponse response = service.getBottlenecks("admin-1");

        assertEquals("ok", response.getSummary());
        assertTrue(response.isAvailable());
    }

    @Test
    void getTaskRedistribution_debeDelegarEnClienteIa() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaRepository.findAll()).thenReturn(List.of());
        when(instanciaRepository.findAll()).thenReturn(List.of());
        when(tareaRepository.findAll()).thenReturn(List.of());
        when(departamentoRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(analyticsIaClient.analyzeTaskRedistribution(org.mockito.ArgumentMatchers.any()))
                .thenReturn(TaskRedistributionAnalyticsResponse.builder()
                        .summary("redistribution")
                        .recommendations(List.of())
                        .source("AI")
                        .available(true)
                        .build());

        TaskRedistributionAnalyticsResponse response = service.getTaskRedistribution("admin-1");

        assertEquals("redistribution", response.getSummary());
    }

    @Test
    void getPolicyImprovement_debeDelegarEnClienteIa() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaRepository.findAll()).thenReturn(List.of());
        when(instanciaRepository.findAll()).thenReturn(List.of());
        when(tareaRepository.findAll()).thenReturn(List.of());
        when(departamentoRepository.findAll()).thenReturn(List.of());
        when(usuarioRepository.findAll()).thenReturn(List.of());
        when(analyticsIaClient.analyzePolicyImprovement(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PolicyImprovementAnalyticsResponse.builder()
                        .summary("policy")
                        .policyIssues(List.of())
                        .source("AI")
                        .available(true)
                        .build());

        PolicyImprovementAnalyticsResponse response = service.getPolicyImprovement("admin-1");

        assertEquals("policy", response.getSummary());
    }
}
