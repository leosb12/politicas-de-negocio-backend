package com.leo.politicas_de_negocio.guide.service;

import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.guide.client.GuideIaClient;
import com.leo.politicas_de_negocio.guide.dto.AdminGuideIaRequest;
import com.leo.politicas_de_negocio.guide.dto.AdminGuideRequest;
import com.leo.politicas_de_negocio.guide.dto.AdminGuideResponse;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AdminGuideServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PoliticaNegocioRepository politicaNegocioRepository;

    @Mock
    private DepartamentoRepository departamentoRepository;

    @Mock
    private GuideIaClient guideIaClient;

    private AutoCloseable mocks;
    private AdminGuideService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new AdminGuideService(
                usuarioRepository,
                politicaNegocioRepository,
                departamentoRepository,
                guideIaClient,
                new AdminGuideFallbackService(),
                new AdminGuideIntentResolver()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void guideAdmin_debeArmarContextoYDelegarALaIa() {
        when(usuarioRepository.findById("admin-1"))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").nombre("Admin").build()));
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy()));
        when(departamentoRepository.findById("dep-1"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder().id("dep-1").nombre("Departamento Tecnico").build()));
        when(guideIaClient.guideAdmin(any(AdminGuideIaRequest.class)))
                .thenReturn(AdminGuideResponse.builder()
                        .answer("Respuesta IA")
                        .source("AI")
                        .available(true)
                        .build());

        AdminGuideRequest request = AdminGuideRequest.builder()
                .screen("POLICY_DESIGNER")
                .question("Sugerime formulario")
                .context(com.leo.politicas_de_negocio.guide.dto.AdminGuideContextRequest.builder()
                        .policyId("pol-1")
                        .selectedNodeId("node-1")
                        .availableActions(List.of("SAVE_POLICY"))
                        .build())
                .build();

        AdminGuideResponse response = service.guideAdmin("admin-1", request);

        assertEquals("Respuesta IA", response.getAnswer());

        ArgumentCaptor<AdminGuideIaRequest> captor = ArgumentCaptor.forClass(AdminGuideIaRequest.class);
        org.mockito.Mockito.verify(guideIaClient).guideAdmin(captor.capture());
        AdminGuideIaRequest iaRequest = captor.getValue();
        assertEquals("admin-1", iaRequest.getUserId());
        assertEquals("Instalacion de medidor", iaRequest.getContext().getPolicyName());
        assertEquals("BORRADOR", iaRequest.getContext().getPolicyStatus());
        assertEquals("ACTIVITY", iaRequest.getContext().getSelectedNode().getType());
        assertEquals(2, iaRequest.getContext().getPolicySummary().getTotalActivities());
        assertTrue(iaRequest.getContext().getAvailableActions().contains("ADD_FORM_FIELD"));
    }

    @Test
    void guideAdmin_debeUsarFallbackSiIaNoResponde() {
        when(usuarioRepository.findById("admin-1"))
                .thenReturn(Optional.of(Usuario.builder().id("admin-1").rol("ADMIN").nombre("Admin").build()));
        when(politicaNegocioRepository.findById("pol-1"))
                .thenReturn(Optional.of(buildPolicy()));
        when(departamentoRepository.findById("dep-1"))
                .thenReturn(Optional.of(com.leo.politicas_de_negocio.departamentos.model.Departamento.builder().id("dep-1").nombre("Departamento Tecnico").build()));
        when(guideIaClient.guideAdmin(any(AdminGuideIaRequest.class))).thenReturn(null);

        AdminGuideRequest request = AdminGuideRequest.builder()
                .screen("POLICY_DESIGNER")
                .question("Puedo activar esta politica?")
                .context(com.leo.politicas_de_negocio.guide.dto.AdminGuideContextRequest.builder()
                        .policyId("pol-1")
                        .selectedNodeId("node-1")
                        .availableActions(List.of("SAVE_POLICY", "ACTIVATE_POLICY"))
                        .build())
                .build();

        AdminGuideResponse response = service.guideAdmin("admin-1", request);

        assertEquals("BACKEND_FALLBACK", response.getSource());
        assertEquals("ERROR", response.getSeverity());
        assertTrue(response.getDetectedIssues().stream().anyMatch(issue -> "MISSING_END_NODE".equals(issue.getType())));
        assertTrue(response.getSuggestedActions().stream().anyMatch(action -> "SAVE_POLICY".equals(action.getAction())));
    }

    private PoliticaNegocio buildPolicy() {
        return PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Instalacion de medidor")
                .estado(EstadoPolitica.BORRADOR)
                .nodos(List.of(
                        Nodo.builder().id("start-1").nombre("Inicio").tipo(TipoNodo.INICIO).build(),
                        Nodo.builder()
                                .id("node-1")
                                .nombre("Evaluar viabilidad tecnica")
                                .tipo(TipoNodo.ACTIVIDAD)
                                .departamentoId("dep-1")
                                .formulario(List.of(
                                        CampoFormulario.builder().campo("Observaciones tecnicas").tipo(TipoCampo.TEXTO).build()
                                ))
                                .build(),
                        Nodo.builder()
                                .id("node-2")
                                .nombre("Aprobar instalacion")
                                .tipo(TipoNodo.ACTIVIDAD)
                                .build(),
                        Nodo.builder().id("decision-1").nombre("Decision tecnica").tipo(TipoNodo.DECISION).build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("start-1").destino("node-1").build(),
                        Conexion.builder().origen("node-1").destino("decision-1").build()
                ))
                .build();
    }
}
