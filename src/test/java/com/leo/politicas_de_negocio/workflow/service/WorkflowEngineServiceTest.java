package com.leo.politicas_de_negocio.workflow.service;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.instancias.service.HistorialInstanciaService;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.CondicionDecision;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowEngineServiceTest {

    @Mock
    private InstanciaPoliticaRepository instanciaRepository;

    @Mock
    private TareaActividadRepository tareaRepository;

    @Mock
    private HistorialInstanciaService historialService;

    private AutoCloseable mocks;
    private WorkflowEngineService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new WorkflowEngineService(instanciaRepository, tareaRepository, historialService);

        AtomicInteger secuenciaTarea = new AtomicInteger(0);
        when(tareaRepository.save(any(TareaActividad.class))).thenAnswer(invocation -> {
            TareaActividad tarea = invocation.getArgument(0);
            if (tarea.getId() == null) {
                tarea.setId("t-" + secuenciaTarea.incrementAndGet());
            }
            return tarea;
        });

        when(tareaRepository.findByInstanciaIdAndNodoIdAndEstadoTareaIn(anyString(), anyString(), anyList()))
                .thenReturn(List.of());

        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(anyString(), anyList()))
                .thenReturn(0L);

        when(instanciaRepository.save(any(InstanciaPolitica.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void iniciarInstancia_debeCrearPrimeraTareaDesdeInicio() {
        InstanciaPolitica instancia = instanciaBase("inst-1");

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nodos(List.of(
                        nodo("n0", TipoNodo.INICIO, null, null),
                        nodo("n1", TipoNodo.ACTIVIDAD, "USUARIO", "u-1")
                ))
                .conexiones(List.of(conexion("n0", "n1")))
                .build();

        service.iniciarInstancia(instancia, politica, "admin-1");

        ArgumentCaptor<TareaActividad> captor = ArgumentCaptor.forClass(TareaActividad.class);
        verify(tareaRepository).save(captor.capture());

        TareaActividad creada = captor.getValue();
        assertEquals("inst-1", creada.getInstanciaId());
        assertEquals("pol-1", creada.getPoliticaId());
        assertEquals("n1", creada.getNodoId());
        assertEquals("USUARIO", creada.getResponsableTipo());
        assertEquals("u-1", creada.getResponsableId());
        assertNotNull(creada.getFechaCreacion());
    }

    @Test
    void avanzarDesdeNodo_enDecisionDebeElegirSalidaSegunContexto() {
        InstanciaPolitica instancia = instanciaBase("inst-2");

        Nodo decision = nodo("n-decision", TipoNodo.DECISION, null, null);
        decision.setCondiciones(List.of(
                CondicionDecision.builder().resultado("SI").siguiente("n-aprueba").build(),
                CondicionDecision.builder().resultado("NO").siguiente("n-rechaza").build()
        ));

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-2")
                .nodos(List.of(
                        nodo("n-actividad", TipoNodo.ACTIVIDAD, "USUARIO", "u-1"),
                        decision,
                        nodo("n-aprueba", TipoNodo.ACTIVIDAD, "DEPARTAMENTO", "dep-1"),
                        nodo("n-rechaza", TipoNodo.ACTIVIDAD, "DEPARTAMENTO", "dep-2")
                ))
                .conexiones(List.of(
                        conexion("n-actividad", "n-decision"),
                        conexion("n-decision", "n-aprueba"),
                        conexion("n-decision", "n-rechaza")
                ))
                .build();

        Map<String, Object> contexto = new HashMap<>();
        contexto.put("resultado", "SI");

        service.avanzarDesdeNodo(instancia, politica, "n-actividad", "func-1", contexto);

        ArgumentCaptor<TareaActividad> captor = ArgumentCaptor.forClass(TareaActividad.class);
        verify(tareaRepository).save(captor.capture());

        TareaActividad creada = captor.getValue();
        assertEquals("n-aprueba", creada.getNodoId());
        assertEquals("dep-1", creada.getResponsableId());
    }

    @Test
    void avanzarDesdeNodo_conJoinDebeEsperarTodasLasRamas() {
        InstanciaPolitica instancia = instanciaBase("inst-3");

        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(eq("inst-3"), anyList()))
                .thenReturn(1L, 0L);

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-3")
                .nodos(List.of(
                        nodo("a-1", TipoNodo.ACTIVIDAD, "USUARIO", "u-1"),
                        nodo("a-2", TipoNodo.ACTIVIDAD, "USUARIO", "u-2"),
                        nodo("j-1", TipoNodo.JOIN, null, null),
                        nodo("sig", TipoNodo.ACTIVIDAD, "DEPARTAMENTO", "dep-1")
                ))
                .conexiones(List.of(
                        conexion("a-1", "j-1"),
                        conexion("a-2", "j-1"),
                        conexion("j-1", "sig")
                ))
                .build();

        service.avanzarDesdeNodo(instancia, politica, "a-1", "func-1", Map.of());

        assertNotNull(instancia.getTokensJoin());
        assertTrue(instancia.getTokensJoin().containsKey("j-1"));
        assertEquals(1, instancia.getTokensJoin().get("j-1").size());

        service.avanzarDesdeNodo(instancia, politica, "a-2", "func-2", Map.of());

        verify(tareaRepository, times(1)).save(any(TareaActividad.class));
        assertTrue(instancia.getTokensJoin().isEmpty());
    }

    @Test
    void avanzarDesdeNodo_enDecisionSinSalidaDebeFallarControladamente() {
        InstanciaPolitica instancia = instanciaBase("inst-5");

        Nodo decision = nodo("n-decision", TipoNodo.DECISION, null, null);
        decision.setCondiciones(List.of(
                CondicionDecision.builder().resultado("SI").siguiente("n-aprueba").build()
        ));

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-5")
                .nodos(List.of(
                        nodo("n-origen", TipoNodo.ACTIVIDAD, "USUARIO", "u-1"),
                        decision,
                        nodo("n-aprueba", TipoNodo.ACTIVIDAD, "USUARIO", "u-2")
                ))
                .conexiones(List.of(
                        conexion("n-origen", "n-decision"),
                        conexion("n-decision", "n-aprueba")
                ))
                .build();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.avanzarDesdeNodo(instancia, politica, "n-origen", "func-1", Map.of("resultado", "NO")));

        assertEquals("No existe salida para el resultado de decision 'NO' en nodo n-decision", ex.getMessage());
        assertEquals(409, ex.getStatus().value());
        verify(tareaRepository, never()).save(any(TareaActividad.class));
    }

    @Test
    void avanzarDesdeNodo_conJoinSinRamasActivasDebePausarInstanciaYFallar() {
        InstanciaPolitica instancia = instanciaBase("inst-6");

        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(eq("inst-6"), anyList()))
                .thenReturn(0L);

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-6")
                .nodos(List.of(
                        nodo("a-1", TipoNodo.ACTIVIDAD, "USUARIO", "u-1"),
                        nodo("a-2", TipoNodo.ACTIVIDAD, "USUARIO", "u-2"),
                        nodo("j-1", TipoNodo.JOIN, null, null),
                        nodo("sig", TipoNodo.ACTIVIDAD, "DEPARTAMENTO", "dep-1")
                ))
                .conexiones(List.of(
                        conexion("a-1", "j-1"),
                        conexion("a-2", "j-1"),
                        conexion("j-1", "sig")
                ))
                .build();

        ApiException ex = assertThrows(ApiException.class,
                () -> service.avanzarDesdeNodo(instancia, politica, "a-1", "func-1", Map.of()));

        assertTrue(ex.getMessage().contains("JOIN pendientes"));
        assertEquals(EstadoInstancia.PAUSADA, instancia.getEstadoInstancia());
        verify(tareaRepository, never()).save(any(TareaActividad.class));
    }

    @Test
    void avanzarDesdeNodo_alLlegarAFinDebeCerrarInstanciaSiNoHayTareasAbiertas() {
        InstanciaPolitica instancia = instanciaBase("inst-4");

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-4")
                .nodos(List.of(
                        nodo("a-1", TipoNodo.ACTIVIDAD, "USUARIO", "u-1"),
                        nodo("fin", TipoNodo.FIN, null, null)
                ))
                .conexiones(List.of(conexion("a-1", "fin")))
                .build();

        service.avanzarDesdeNodo(instancia, politica, "a-1", "func-1", Map.of());

        assertEquals(EstadoInstancia.FINALIZADA, instancia.getEstadoInstancia());
    }

    private InstanciaPolitica instanciaBase(String id) {
        return InstanciaPolitica.builder()
                .id(id)
                .politicaId("pol-x")
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .tokensJoin(new HashMap<>())
                .datosContexto(new HashMap<>())
                .build();
    }

    private Nodo nodo(String id, TipoNodo tipo, String responsableTipo, String responsableId) {
        return Nodo.builder()
                .id(id)
                .tipo(tipo)
                .nombre(id)
                .responsableTipo(responsableTipo)
                .responsableId(responsableId)
                .build();
    }

    private Conexion conexion(String origen, String destino) {
        return Conexion.builder()
                .origen(origen)
                .destino(destino)
                .build();
    }
}
