package com.leo.politicas_de_negocio.instancias.service;

import com.leo.politicas_de_negocio.instancias.dto.InstanciaDetalleResponse;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InstanciaPoliticaServiceTest {

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
