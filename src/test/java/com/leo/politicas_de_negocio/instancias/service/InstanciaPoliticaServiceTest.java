package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.departamentos.model.Departamento;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.dto.SeguimientoInstanciaResponse;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InstanciaPoliticaServiceTest {

    @Mock
    private InstanciaPoliticaRepository instanciaRepository;

    @Mock
    private PoliticaNegocioRepository politicaRepository;

    @Mock
    private DepartamentoRepository departamentoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private HistorialInstanciaService historialService;

    @Mock
    private WorkflowEngineService workflowEngineService;

    @Mock
    private TareaActividadRepository tareaRepository;

    private AutoCloseable mocks;
    private InstanciaPoliticaService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new InstanciaPoliticaService(
                instanciaRepository,
                politicaRepository,
                departamentoRepository,
                usuarioRepository,
                historialService,
                workflowEngineService,
                tareaRepository
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void listarDetalle_debeIncluirNombreDePolitica() {
        Usuario actor = Usuario.builder()
                .id("user-1")
                .rol("CLIENTE")
                .activo(true)
                .build();

        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("user-1")
                .politicaId("pol-1")
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Solicitud de vacaciones")
                .build();

        when(usuarioRepository.findByIdAndActivo("user-1", true)).thenReturn(Optional.of(actor));
        when(instanciaRepository.findByCreadaPorOrderByFechaCreacionDesc("user-1"))
                .thenReturn(List.of(instancia));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(tareaRepository.countByInstanciaId("inst-1")).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(eq("inst-1"), anyList())).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.COMPLETADA)).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.CANCELADA)).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.RECHAZADA)).thenReturn(0L);

        List<InstanciaDetalleResponse> response = service.listarDetalle("user-1", null);

        assertEquals(1, response.size());
        assertEquals("Solicitud de vacaciones", response.get(0).getPoliticaNombre());
    }

    @Test
    void obtenerSeguimientoPorId_debeRetornarDiagramaConNodoActual() {
        Usuario actor = Usuario.builder()
                .id("cliente-1")
                .nombre("Cliente Demo")
                .rol("CLIENTE")
                .activo(true)
                .build();

        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("cliente-1")
                .politicaId("pol-1")
                .politicaVersion(3L)
                .codigoTramite("TRM-1")
                .estadoInstancia(EstadoInstancia.EN_CURSO)
                .fechaCreacion(java.time.LocalDateTime.now())
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Solicitud demo")
                .estado(com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica.ACTIVA)
                .laneOrientation("VERTICAL")
                .laneWidth(320d)
                .laneHeight(220d)
                .nodos(List.of(
                        Nodo.builder().id("inicio").tipo(TipoNodo.INICIO).nombre("Inicio").build(),
                        Nodo.builder().id("revision").tipo(TipoNodo.ACTIVIDAD).nombre("Revision").departamentoId("dep-1").responsableTipo("DEPARTAMENTO").responsableId("dep-1").posX(10d).posY(20d).build(),
                        Nodo.builder().id("firma").tipo(TipoNodo.ACTIVIDAD).nombre("Firma").departamentoId("dep-2").responsableTipo("DEPARTAMENTO").responsableId("dep-2").posX(30d).posY(40d).build()
                ))
                .conexiones(List.of(
                        Conexion.builder().origen("inicio").destino("revision").build(),
                        Conexion.builder().origen("revision").destino("firma").build()
                ))
                .build();

        TareaActividad tareaCompletada = TareaActividad.builder()
                .id("t-1")
                .instanciaId("inst-1")
                .politicaId("pol-1")
                .nodoId("revision")
                .nombreNodo("Revision")
                .responsableTipo("DEPARTAMENTO")
                .responsableId("dep-1")
                .estadoTarea(EstadoTarea.COMPLETADA)
                .build();

        TareaActividad tareaActual = TareaActividad.builder()
                .id("t-2")
                .instanciaId("inst-1")
                .politicaId("pol-1")
                .nodoId("firma")
                .nombreNodo("Firma")
                .responsableTipo("DEPARTAMENTO")
                .responsableId("dep-2")
                .estadoTarea(EstadoTarea.PENDIENTE)
                .build();

        when(usuarioRepository.findByIdAndActivo("cliente-1", true)).thenReturn(Optional.of(actor));
        when(usuarioRepository.findById("cliente-1")).thenReturn(Optional.of(actor));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(tareaRepository.findByInstanciaIdOrderByFechaCreacionAsc("inst-1"))
                .thenReturn(List.of(tareaCompletada, tareaActual));
        when(departamentoRepository.findById("dep-1")).thenReturn(Optional.of(Departamento.builder().id("dep-1").nombre("Mesa de Entrada").build()));
        when(departamentoRepository.findById("dep-2")).thenReturn(Optional.of(Departamento.builder().id("dep-2").nombre("Gerencia").build()));

        SeguimientoInstanciaResponse response = service.obtenerSeguimientoPorId("cliente-1", "inst-1");

        assertEquals("pol-1", response.getPoliticaId());
        assertEquals(3, response.getNodos().size());
        assertEquals(2, response.getConexiones().size());
        assertEquals(List.of("firma"), response.getNodosActualesIds());
        assertEquals("dep-2", response.getDepartamentosActuales().get(0).getDepartamentoId());
        assertEquals("Gerencia", response.getDepartamentosActuales().get(0).getDepartamentoNombre());
        assertTrue(response.getNodos().stream()
                .anyMatch(nodo -> "firma".equals(nodo.getId())
                        && "ACTUAL".equals(nodo.getEstadoSeguimiento())
                        && "t-2".equals(nodo.getTareaActualId())));
    }

    @Test
    void obtenerDetallePorId_debePermitirFuncionarioConTareaAsociada() {
        Usuario actor = Usuario.builder()
                .id("func-1")
                .rol("FUNCIONARIO")
                .departamentoId("dep-1")
                .activo(true)
                .build();

        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("otro")
                .politicaId("pol-1")
                .build();

        when(usuarioRepository.findByIdAndActivo("func-1", true)).thenReturn(Optional.of(actor));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(tareaRepository.existsByInstanciaIdAndAsignadoA("inst-1", "func-1")).thenReturn(false);
        when(tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId("inst-1", "USUARIO", "func-1"))
                .thenReturn(false);
        when(tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId("inst-1", "DEPARTAMENTO", "dep-1"))
                .thenReturn(true);
        when(tareaRepository.countByInstanciaId("inst-1")).thenReturn(2L);
        when(tareaRepository.countByInstanciaIdAndEstadoTareaIn(eq("inst-1"), anyList())).thenReturn(1L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.COMPLETADA)).thenReturn(1L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.CANCELADA)).thenReturn(0L);
        when(tareaRepository.countByInstanciaIdAndEstadoTarea("inst-1", EstadoTarea.RECHAZADA)).thenReturn(0L);

        InstanciaDetalleResponse response = service.obtenerDetallePorId("func-1", "inst-1");

        assertEquals("inst-1", response.getId());
        assertEquals(2L, response.getTotalTareas());
        assertEquals(1L, response.getTareasAbiertas());
    }

    @Test
    void obtenerDetallePorId_debeBloquearSiNoEsCreadorNiParticipante() {
        Usuario actor = Usuario.builder()
                .id("func-1")
                .rol("FUNCIONARIO")
                .departamentoId("dep-1")
                .activo(true)
                .build();

        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("otro")
                .politicaId("pol-1")
                .build();

        when(usuarioRepository.findByIdAndActivo("func-1", true)).thenReturn(Optional.of(actor));
        when(instanciaRepository.findById("inst-1")).thenReturn(Optional.of(instancia));
        when(tareaRepository.existsByInstanciaIdAndAsignadoA("inst-1", "func-1")).thenReturn(false);
        when(tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId("inst-1", "USUARIO", "func-1"))
                .thenReturn(false);
        when(tareaRepository.existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId("inst-1", "DEPARTAMENTO", "dep-1"))
                .thenReturn(false);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.obtenerDetallePorId("func-1", "inst-1"));

        assertEquals(403, ex.getStatus().value());
    }
}
