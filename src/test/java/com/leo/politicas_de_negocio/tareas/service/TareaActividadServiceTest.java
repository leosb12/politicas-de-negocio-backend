package com.leo.politicas_de_negocio.tareas.service;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.instancias.service.HistorialInstanciaService;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.tareas.dto.CompletarTareaRequest;
import com.leo.politicas_de_negocio.tareas.dto.TareaDetalleResponse;
import com.leo.politicas_de_negocio.tareas.dto.TareaMiaResponse;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import com.leo.politicas_de_negocio.workflow.service.WorkflowEngineService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TareaActividadServiceTest {

    @Mock
    private TareaActividadRepository tareaRepository;

    @Mock
    private InstanciaPoliticaRepository instanciaRepository;

    @Mock
    private PoliticaNegocioRepository politicaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HistorialInstanciaService historialService;

    @Mock
    private WorkflowEngineService workflowEngineService;

    private AutoCloseable mocks;
    private TareaActividadService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new TareaActividadService(
                tareaRepository,
                instanciaRepository,
                politicaRepository,
                usuarioRepository,
                historialService,
                workflowEngineService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void completarTarea_debeBloquearSiActorNoEsResponsable() {
        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(usuario("u-1", "dep-1")));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea("t-1", "inst-1", "USUARIO", "u-2", EstadoTarea.PENDIENTE)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.completarTarea("u-1", "t-1", new CompletarTareaRequest()));

        assertEquals(403, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("otro usuario"));
        verify(tareaRepository, never()).save(any(TareaActividad.class));
    }

    @Test
    void listarMisTareasResumen_debeRetornarSoloTareasDelActor() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tareaUsuario = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.PENDIENTE);
        tareaUsuario.setNombreNodo("Revisar solicitud");
        TareaActividad tareaDepartamento = tarea("t-2", "inst-2", "DEPARTAMENTO", "dep-1", EstadoTarea.PENDIENTE);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findByResponsableTipoAndResponsableIdAndEstadoTareaInOrderByFechaCreacionAsc(
                "USUARIO", "u-1", List.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_PROCESO)
        )).thenReturn(List.of(tareaUsuario));
        when(tareaRepository.findByResponsableTipoAndResponsableIdAndEstadoTareaInOrderByFechaCreacionAsc(
                "DEPARTAMENTO", "dep-1", List.of(EstadoTarea.PENDIENTE, EstadoTarea.EN_PROCESO)
        )).thenReturn(List.of(tareaDepartamento));

        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia("inst-1", "pol-1", 1L)));
        when(instanciaRepository.findById("inst-2")).thenReturn(Optional.of(instancia("inst-2", "pol-2", 1L)));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica("pol-1", EstadoPolitica.ACTIVA, 1L)));
        when(politicaRepository.findById("pol-2")).thenReturn(Optional.of(politica("pol-2", EstadoPolitica.ACTIVA, 1L)));

        List<TareaMiaResponse> result = service.listarMisTareasResumen("u-1");

        assertEquals(2, result.size());
        assertEquals("t-1", result.get(0).getId());
        assertEquals("t-2", result.get(1).getId());
    }

    @Test
    void obtenerDetalleTarea_debeBloquearSiNoPerteneceAlActor() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tareaAjena = tarea("t-1", "inst-1", "USUARIO", "u-2", EstadoTarea.PENDIENTE);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tareaAjena));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.obtenerDetalleTarea("u-1", "t-1"));

        assertEquals(403, ex.getStatus().value());
    }

    @Test
    void obtenerDetalleTarea_debeRetornarDetalleCompleto() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tarea = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.PENDIENTE);
        tarea.setFormularioDefinicion(List.of(CampoFormulario.builder().campo("monto").tipo(TipoCampo.NUMERO).build()));
        tarea.setFormularioRespuesta(Map.of("monto", 100));

        InstanciaPolitica instancia = instancia("inst-1", "pol-1", 1L);
        instancia.setCreadaPor("u-9");

        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.ACTIVA, 1L);
        politica.setNombre("Politica Test");

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea));
        when(tareaRepository.existsByInstanciaIdAndAsignadoA("inst-1", "u-1")).thenReturn(false);
        when(tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId("inst-1", "USUARIO", "u-1"))
                .thenReturn(true);
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(tareaRepository.countByInstanciaId("inst-1")).thenReturn(1L);
        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(eq("inst-1"), any())).thenReturn(1L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.COMPLETADA)).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.CANCELADA)).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.RECHAZADA)).thenReturn(0L);
        when(historialService.listarPorInstancia("inst-1")).thenReturn(List.of());

        TareaDetalleResponse result = service.obtenerDetalleTarea("u-1", "t-1");

        assertEquals("t-1", result.getId());
        assertEquals("n-1", result.getActividad().getNodoId());
        assertEquals("pol-1", result.getPolitica().getId());
        assertEquals("inst-1", result.getInstancia().getId());
    }

    @Test
    void tomarTarea_debePasarAEnProcesoYAsignarActor() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tarea = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.PENDIENTE);
        InstanciaPolitica instancia = instancia("inst-1", "pol-1", 1L);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(tareaRepository.save(any(TareaActividad.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanciaRepository.save(any(InstanciaPolitica.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TareaActividad result = service.tomarTarea("u-1", "t-1");

        assertEquals(EstadoTarea.EN_PROCESO, result.getEstadoTarea());
        assertEquals("u-1", result.getAsignadoA());
        assertTrue(result.getFechaInicio() != null);
        verify(instanciaRepository).save(argThat(inst -> inst != null && inst.getFechaActualizacion() != null));
    }

    @Test
    void tomarTarea_debeBloquearSiEstadoNoEsTomable() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tarea = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.COMPLETADA);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.tomarTarea("u-1", "t-1"));

        assertEquals(409, ex.getStatus().value());
    }

    @Test
    void completarTarea_debeBloquearSiYaFueCompletada() {
        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(usuario("u-1", "dep-1")));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.COMPLETADA)));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.completarTarea("u-1", "t-1", new CompletarTareaRequest()));

        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("Solo se puede completar"));
        verify(tareaRepository, never()).save(any(TareaActividad.class));
    }

    @Test
    void completarTarea_debePermitirContinuidadCuandoPoliticaNoActivaMismaVersion() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tarea = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.PENDIENTE);
        InstanciaPolitica instancia = instancia("inst-1", "pol-1", 5L);
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.DESHABILITADA, 5L);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(tareaRepository.save(any(TareaActividad.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanciaRepository.save(any(InstanciaPolitica.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompletarTareaRequest request = new CompletarTareaRequest();
        request.setFormularioRespuesta(Map.of("resultado", "OK"));
        request.setObservaciones("completada");

        TareaActividad result = service.completarTarea("u-1", "t-1", request);

        assertEquals(EstadoTarea.COMPLETADA, result.getEstadoTarea());
        verify(historialService).registrar(
                eq("inst-1"),
                eq(null),
                eq("POLITICA_NO_ACTIVA_CONTINUIDAD"),
                eq("u-1"),
                any(String.class)
        );
    }

    @Test
    void completarTarea_debeBloquearSiPoliticaCambioVersion() {
        Usuario actor = usuario("u-1", "dep-1");
        TareaActividad tarea = tarea("t-1", "inst-1", "USUARIO", "u-1", EstadoTarea.PENDIENTE);
        InstanciaPolitica instancia = instancia("inst-1", "pol-1", 5L);
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.DESHABILITADA, 6L);

        when(usuarioRepository.findByIdAndActivo("u-1", true)).thenReturn(Optional.of(actor));
        when(tareaRepository.findById("t-1")).thenReturn(Optional.of(tarea));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.completarTarea("u-1", "t-1", new CompletarTareaRequest()));

        assertEquals(409, ex.getStatus().value());
        assertTrue(ex.getMessage().contains("cambio de version"));
        verify(tareaRepository, never()).save(any(TareaActividad.class));
    }

    private Usuario usuario(String id, String departamentoId) {
        return Usuario.builder()
                .id(id)
                .rol("FUNCIONARIO")
                .departamentoId(departamentoId)
                .activo(true)
                .build();
    }

    private TareaActividad tarea(String id, String instanciaId, String responsableTipo, String responsableId, EstadoTarea estado) {
        return TareaActividad.builder()
                .id(id)
                .instanciaId(instanciaId)
                .politicaId("pol-1")
                .nodoId("n-1")
                .responsableTipo(responsableTipo)
                .responsableId(responsableId)
                .estadoTarea(estado)
                .build();
    }

    private InstanciaPolitica instancia(String id, String politicaId, Long version) {
        return InstanciaPolitica.builder()
                .id(id)
                .politicaId(politicaId)
                .politicaVersion(version)
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .datosContexto(new HashMap<>())
                .build();
    }

    private PoliticaNegocio politica(String id, EstadoPolitica estado, Long version) {
        return PoliticaNegocio.builder()
                .id(id)
                .estado(estado)
                .secuenciaColaboracion(version)
                .build();
    }
}
