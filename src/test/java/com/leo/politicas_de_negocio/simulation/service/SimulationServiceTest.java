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
import com.leo.politicas_de_negocio.simulation.model.SimulationRun;
import com.leo.politicas_de_negocio.simulation.repository.SimulationRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SimulationServiceTest {

    @Mock
    private SimulationRepository simulationRepository;
    @Mock
    private PoliticaNegocioRepository politicaNegocioRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SimulationIaClient simulationIaClient;

    private AutoCloseable mocks;
    private SimulationService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new SimulationService(
                simulationRepository,
                politicaNegocioRepository,
                usuarioRepository,
                simulationIaClient
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void runSimulation_debePersistirYRetornarMetricas() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy("pol-1", "Politica A")));
        when(simulationRepository.save(ArgumentMatchers.any(SimulationRun.class)))
                .thenAnswer(invocation -> {
                    SimulationRun run = invocation.getArgument(0);
                    run.setId("sim-1");
                    run.setCreatedAt(LocalDateTime.of(2026, 4, 23, 10, 0));
                    return run;
                });

        SimulationRunResponse response = service.runSimulation(
                "admin-1",
                "pol-1",
                SimulationRunRequest.builder()
                        .instances(20)
                        .baseNodeDurationHours(2.0d)
                        .variabilityPercent(0.0d)
                        .randomSeed(42L)
                        .build()
        );

        assertEquals("sim-1", response.getSimulationId());
        assertEquals("pol-1", response.getPolicyId());
        assertEquals(20L, response.getResult().getInstancesSimulated());
        assertTrue(response.getResult().getAverageEstimatedTimeHours() > 0.0d);
        assertFalse(response.getResult().getNodeStats().isEmpty());
        assertEquals(1, response.getResult().getDecisionStats().size());
    }

    @Test
    void comparePolicies_debeElegirPoliticaMasEficienteYAgregarIaSiSeSolicita() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy("pol-1", "Politica A")));
        when(politicaNegocioRepository.findById("pol-2"))
                .thenReturn(Optional.of(buildSimplerPolicy("pol-2", "Politica B")));
        when(simulationIaClient.comparePolicies(ArgumentMatchers.any()))
                .thenReturn(SimulationAiInsightResponse.builder()
                        .summary("Analisis IA")
                        .source("AI")
                        .available(true)
                        .build());

        PolicyComparisonResponse response = service.comparePolicies(
                "admin-1",
                SimulationComparisonRequest.builder()
                        .firstPolicyId("pol-1")
                        .secondPolicyId("pol-2")
                        .instances(10)
                        .variabilityPercent(0.0d)
                        .baseNodeDurationHours(2.0d)
                        .includeAiAnalysis(true)
                        .randomSeed(9L)
                        .build()
        );

        assertNotNull(response.getResult());
        assertEquals("AI", response.getResult().getAiSource());
        assertTrue(response.getResult().isAiAvailable());
        assertNotNull(response.getResult().getMoreEfficientPolicyId());
        assertEquals(10L, response.getResult().getFirstPolicyResult().getInstancesSimulated());
    }

    @Test
    void getSimulationById_debeFallarSiNoExiste() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(simulationRepository.findById("sim-404")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.getSimulationById("admin-1", "sim-404"));

        assertEquals(404, ex.getStatus().value());
    }

    @Test
    void getSimulationsByPolicy_debeFallarSiPoliticaNoExiste() {
        when(usuarioRepository.findByIdAndActivo("admin-1", true))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").activo(true).build()));
        when(politicaNegocioRepository.findById("pol-x")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.getSimulationsByPolicy("admin-1", "pol-x"));

        assertEquals(404, ex.getStatus().value());
    }

    private PoliticaNegocio buildPolicy(String id, String name) {
        return PoliticaNegocio.builder()
                .id(id)
                .nombre(name)
                .nodos(List.of(
                        Nodo.builder().id("n-start").nombre("Inicio").tipo(TipoNodo.INICIO).build(),
                        Nodo.builder().id("n-act").nombre("Revision").tipo(TipoNodo.ACTIVIDAD).build(),
                        Nodo.builder().id("n-dec").nombre("Decision").tipo(TipoNodo.DECISION).build(),
                        Nodo.builder().id("n-ok").nombre("Aprobado").tipo(TipoNodo.ACTIVIDAD).formulario(List.of()).build(),
                        Nodo.builder().id("n-end").nombre("Fin").tipo(TipoNodo.FIN).build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("n-start").destino("n-act").build(),
                        Conexion.builder().origen("n-act").destino("n-dec").build(),
                        Conexion.builder().origen("n-dec").destino("n-ok").build(),
                        Conexion.builder().origen("n-dec").destino("n-end").build(),
                        Conexion.builder().origen("n-ok").destino("n-end").build()
                ))
                .build();
    }

    private PoliticaNegocio buildSimplerPolicy(String id, String name) {
        return PoliticaNegocio.builder()
                .id(id)
                .nombre(name)
                .nodos(List.of(
                        Nodo.builder().id("s-start").nombre("Inicio").tipo(TipoNodo.INICIO).build(),
                        Nodo.builder().id("s-act").nombre("Revision rapida").tipo(TipoNodo.ACTIVIDAD).build(),
                        Nodo.builder().id("s-end").nombre("Fin").tipo(TipoNodo.FIN).build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("s-start").destino("s-act").build(),
                        Conexion.builder().origen("s-act").destino("s-end").build()
                ))
                .build();
    }
}
