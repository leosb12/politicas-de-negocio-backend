package com.leo.politicas_de_negocio.politicas.service;

import com.leo.politicas_de_negocio.colaboracion.repository.EventoColaboracionAplicadoRepository;
import com.leo.politicas_de_negocio.colaboracion.repository.SnapshotColaboracionPoliticaRepository;
import com.leo.politicas_de_negocio.colaboracion.service.PoliticaPresenciaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.politicas_de_negocio.departamentos.repository.DepartamentoRepository;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.dto.CreatePoliticaRequest;
import com.leo.politicas_de_negocio.politicas.dto.UpdateFlujoRequest;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoNodo;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoPolitica;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.ConfiguracionDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosSeccion;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaAuditoriaRepository;
import com.leo.politicas_de_negocio.analiticas.service.SystemAuditService;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoliticaNegocioServiceTest {

    @Mock
    private PoliticaNegocioRepository politicaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private DepartamentoRepository departamentoRepository;

    @Mock
    private EventoColaboracionAplicadoRepository eventoRepository;

    @Mock
    private SnapshotColaboracionPoliticaRepository snapshotRepository;

    @Mock
    private PoliticaPresenciaService presenciaService;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private InstanciaPoliticaRepository instanciaPoliticaRepository;

    @Mock
    private DocumentoColaborativoMetadataService documentoColaborativoMetadataService;

    @Mock
    private PoliticaAuditoriaRepository politicaAuditoriaRepository;

    @Mock
    private SystemAuditService systemAuditService;

    private AutoCloseable mocks;
    private PoliticaNegocioService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new PoliticaNegocioService(
                politicaRepository,
                usuarioRepository,
                departamentoRepository,
                eventoRepository,
                snapshotRepository,
                presenciaService,
                mongoTemplate,
                instanciaPoliticaRepository,
                documentoColaborativoMetadataService,
                politicaAuditoriaRepository,
                systemAuditService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void eliminarPolitica_debePermitirEliminarSiEsBorradorSinUso() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.BORRADOR);
        prepararEscenarioBaseEliminacion(politica);

        service.eliminarPolitica("admin-1", "pol-1");

        verify(politicaRepository).delete(politica);
    }

    @Test
    void guardarFlujo_sincronizaPermisosDeDocumentoColaborativoYaCreado() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.BORRADOR);
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaRepository.save(any(PoliticaNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanciaPoliticaRepository.findByPoliticaIdOrderByFechaCreacionDesc("pol-1"))
                .thenReturn(List.of(InstanciaPolitica.builder()
                        .id("tramite-1")
                        .creadaPor("cliente-1")
                        .politicaId("pol-1")
                        .build()));

        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setCampoFormularioId("cv");
        DocumentoColaborativoMetadata.PermisosEdicion permisosViejos = new DocumentoColaborativoMetadata.PermisosEdicion();
        permisosViejos.setDepartamentos(List.of("dept-1"));
        metadata.setPermisosEdicion(permisosViejos);

        when(documentoColaborativoMetadataService.listarPorTramite("cliente-1", "tramite-1"))
                .thenReturn(List.of(metadata));

        ConfiguracionDocumento config = ConfiguracionDocumento.builder()
                .tipoDocumento("WORD")
                .modoColaboracion("PERSONALIZADO")
                .permisosEdicion(PermisosSeccion.builder()
                        .departamentos(List.of())
                        .roles(List.of())
                        .usuarios(List.of("user-1"))
                        .build())
                .build();
        Nodo nodo = Nodo.builder()
                .id("nodo-1")
                .tipo(TipoNodo.ACTIVIDAD)
                .formulario(List.of(CampoFormulario.builder()
                        .campo("cv")
                        .tipo("DOCUMENTO_COLABORATIVO")
                        .configuracionDocumento(config)
                        .build()))
                .build();
        UpdateFlujoRequest request = new UpdateFlujoRequest();
        request.setNodos(List.of(nodo));
        request.setConexiones(List.of());

        service.guardarFlujo("admin-1", "pol-1", request);

        ArgumentCaptor<ConfiguracionDocumento> configCaptor = ArgumentCaptor.forClass(ConfiguracionDocumento.class);
        verify(documentoColaborativoMetadataService).actualizarConfiguracionDesdeCampo(eq(metadata), configCaptor.capture());

        ConfiguracionDocumento sincronizada = configCaptor.getValue();
        assertTrue(sincronizada.getPermisosEdicion().getDepartamentos().isEmpty());
        assertEquals(List.of("user-1"), sincronizada.getPermisosEdicion().getUsuarios());
    }

    @Test
    void guardarFlujo_preservaDepartamentosMarcadosEnPermisosDocumentoColaborativo() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.BORRADOR);
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaRepository.save(any(PoliticaNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ConfiguracionDocumento config = ConfiguracionDocumento.builder()
                .tipoDocumento("WORD")
                .permisosEdicion(PermisosSeccion.builder()
                        .departamentos(List.of("dept-forzado"))
                        .roles(List.of())
                        .usuarios(List.of("user-1"))
                        .build())
                .build();
        Nodo nodo = Nodo.builder()
                .id("nodo-1")
                .tipo(TipoNodo.ACTIVIDAD)
                .formulario(List.of(CampoFormulario.builder()
                        .campo("cv")
                        .tipo("DOCUMENTO_COLABORATIVO")
                        .configuracionDocumento(config)
                        .build()))
                .build();
        UpdateFlujoRequest request = new UpdateFlujoRequest();
        request.setNodos(List.of(nodo));
        request.setConexiones(List.of());

        PoliticaNegocio result = service.guardarFlujo("admin-1", "pol-1", request);

        ConfiguracionDocumento savedConfig = result.getNodos().get(0).getFormulario().get(0).getConfiguracionDocumento();
        assertEquals(List.of("dept-forzado"), savedConfig.getPermisosEdicion().getDepartamentos());
        assertEquals(List.of("user-1"), savedConfig.getPermisosEdicion().getUsuarios());
    }

    @Test
    void guardarFlujo_preservaPermisosImpresionDocumentoColaborativo() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.BORRADOR);
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaRepository.save(any(PoliticaNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanciaPoliticaRepository.findByPoliticaIdOrderByFechaCreacionDesc("pol-1"))
                .thenReturn(List.of(InstanciaPolitica.builder()
                        .id("tramite-1")
                        .creadaPor("cliente-1")
                        .politicaId("pol-1")
                        .build()));

        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setCampoFormularioId("doc");
        when(documentoColaborativoMetadataService.listarPorTramite("cliente-1", "tramite-1"))
                .thenReturn(List.of(metadata));

        ConfiguracionDocumento config = ConfiguracionDocumento.builder()
                .tipoDocumento("WORD")
                .permisosImpresion(PermisosSeccion.builder()
                        .departamentos(List.of("dep-1"))
                        .roles(List.of("FUNCIONARIO"))
                        .usuarios(List.of("user-1"))
                        .build())
                .build();
        Nodo nodo = Nodo.builder()
                .id("nodo-1")
                .tipo(TipoNodo.ACTIVIDAD)
                .formulario(List.of(CampoFormulario.builder()
                        .campo("doc")
                        .tipo("DOCUMENTO_COLABORATIVO")
                        .configuracionDocumento(config)
                        .build()))
                .build();
        UpdateFlujoRequest request = new UpdateFlujoRequest();
        request.setNodos(List.of(nodo));
        request.setConexiones(List.of());

        PoliticaNegocio result = service.guardarFlujo("admin-1", "pol-1", request);

        ConfiguracionDocumento savedConfig = result.getNodos().get(0).getFormulario().get(0).getConfiguracionDocumento();
        assertEquals(List.of("dep-1"), savedConfig.getPermisosImpresion().getDepartamentos());
        assertEquals(List.of("FUNCIONARIO"), savedConfig.getPermisosImpresion().getRoles());
        assertEquals(List.of("user-1"), savedConfig.getPermisosImpresion().getUsuarios());

        ArgumentCaptor<ConfiguracionDocumento> configCaptor = ArgumentCaptor.forClass(ConfiguracionDocumento.class);
        verify(documentoColaborativoMetadataService).actualizarConfiguracionDesdeCampo(eq(metadata), configCaptor.capture());
        assertEquals(List.of("user-1"), configCaptor.getValue().getPermisosImpresion().getUsuarios());
    }

    @Test
    void updateFlujoRequest_debeAceptarAliasYCamposCompatiblesDePermisosImpresion() throws Exception {
        String json = """
                {
                  "nodos": [
                    {
                      "id": "nodo-1",
                      "tipo": "ACTIVIDAD",
                      "formulario": [
                        {
                          "campo": "doc",
                          "tipo": "DOCUMENTO_COLABORATIVO",
                          "configuracionDocumento": {
                            "tipoDocumento": "WORD",
                            "permisosImpresionUsuarios": ["user-1"],
                            "permisosImpresionRoles": ["FUNCIONARIO"],
                            "permisosImpresionDepartamentos": ["dep-1"]
                          }
                        }
                      ]
                    },
                    {
                      "id": "nodo-2",
                      "tipo": "ACTIVIDAD",
                      "formulario": [
                        {
                          "campo": "doc2",
                          "tipo": "DOCUMENTO_COLABORATIVO",
                          "configuracionDocumento": {
                            "tipoDocumento": "WORD",
                            "permisosImprimir": {
                              "usuarios": ["user-2"],
                              "roles": ["ADMIN"],
                              "departamentos": ["dep-2"]
                            }
                          }
                        }
                      ]
                    }
                  ],
                  "conexiones": []
                }
                """;

        UpdateFlujoRequest request = new ObjectMapper().readValue(json, UpdateFlujoRequest.class);

        ConfiguracionDocumento config1 = request.getNodos().get(0).getFormulario().get(0).getConfiguracionDocumento();
        assertEquals(List.of("user-1"), config1.getPermisosImpresion().getUsuarios());
        assertEquals(List.of("FUNCIONARIO"), config1.getPermisosImpresion().getRoles());
        assertEquals(List.of("dep-1"), config1.getPermisosImpresion().getDepartamentos());

        ConfiguracionDocumento config2 = request.getNodos().get(1).getFormulario().get(0).getConfiguracionDocumento();
        assertEquals(List.of("user-2"), config2.getPermisosImpresion().getUsuarios());
        assertEquals(List.of("ADMIN"), config2.getPermisosImpresion().getRoles());
        assertEquals(List.of("dep-2"), config2.getPermisosImpresion().getDepartamentos());
    }

    @Test
    void guardarFlujo_noDebeFallarSiSincronizacionDocumentalFalla() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.PAUSADA);
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(politicaRepository.save(any(PoliticaNegocio.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(instanciaPoliticaRepository.findByPoliticaIdOrderByFechaCreacionDesc("pol-1"))
                .thenReturn(List.of(InstanciaPolitica.builder()
                        .id("tramite-1")
                        .creadaPor("cliente-1")
                        .politicaId("pol-1")
                        .build()));
        when(documentoColaborativoMetadataService.listarPorTramite("cliente-1", "tramite-1"))
                .thenThrow(new RuntimeException("DynamoDB no disponible"));

        ConfiguracionDocumento config = ConfiguracionDocumento.builder()
                .tipoDocumento("WORD")
                .permisosEdicion(PermisosSeccion.builder()
                        .departamentos(List.of("dept-1"))
                        .roles(List.of())
                        .usuarios(List.of())
                        .build())
                .build();
        Nodo nodo = Nodo.builder()
                .id("nodo-1")
                .tipo(TipoNodo.ACTIVIDAD)
                .formulario(List.of(CampoFormulario.builder()
                        .campo("doc")
                        .tipo("DOCUMENTO_COLABORATIVO")
                        .configuracionDocumento(config)
                        .build()))
                .build();
        UpdateFlujoRequest request = new UpdateFlujoRequest();
        request.setNodos(List.of(nodo));
        request.setConexiones(List.of());

        PoliticaNegocio result = service.guardarFlujo("admin-1", "pol-1", request);

        assertEquals(List.of(nodo), result.getNodos());
        verify(politicaRepository).save(any(PoliticaNegocio.class));
    }

    @Test
    void eliminarPolitica_debeBloquearSiEstadoNoEsEliminable() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.ACTIVA);
        prepararEscenarioBaseEliminacion(politica);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.eliminarPolitica("admin-1", "pol-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("BORRADOR o DESHABILITADA"));
        verify(politicaRepository, never()).delete(any());
    }

    @Test
    void eliminarPolitica_debeBloquearSiTieneHistorial() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.DESHABILITADA);
        prepararEscenarioBaseEliminacion(politica);
        when(eventoRepository.existsByPoliticaId("pol-1")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.eliminarPolitica("admin-1", "pol-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("historial"));
        verify(politicaRepository, never()).delete(any());
    }

    @Test
    void eliminarPolitica_debeBloquearSiEstaEnUsoActualmente() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.DESHABILITADA);
        prepararEscenarioBaseEliminacion(politica);
        when(presenciaService.tieneActividadActiva("pol-1")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.eliminarPolitica("admin-1", "pol-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("siendo utilizada actualmente"));
        verify(politicaRepository, never()).delete(any());
    }

    @Test
    void eliminarPolitica_debeBloquearSiTieneReferenciasExternas() {
        PoliticaNegocio politica = politica("pol-1", EstadoPolitica.DESHABILITADA);
        prepararEscenarioBaseEliminacion(politica);

        when(mongoTemplate.getCollectionNames()).thenReturn(Set.of("politicas_negocio", "tramites_instancia"));
        when(mongoTemplate.exists(any(Query.class), eq("tramites_instancia"))).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> service.eliminarPolitica("admin-1", "pol-1"));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("tramites_instancia"));
        verify(politicaRepository, never()).delete(any());
    }

    @Test
    void puedeIniciarPolitica_debePermitirSoloUsuariosEnPoliticaExterna() {
        PoliticaNegocio politica = politicaConTipo("pol-ext", TipoPolitica.EXTERNA, null);

        assertTrue(service.puedeIniciarPolitica(usuario("u-1", "USUARIO", null), politica));
        assertTrue(service.puedeIniciarPolitica(usuario("u-2", "usuario", null), politica));
        assertTrue(service.puedeIniciarPolitica(usuario("u-3", "USUARIO", "dep-1"), politica));
        assertTrue(!service.puedeIniciarPolitica(usuario("a-1", "ADMIN", null), politica));
        assertTrue(!service.puedeIniciarPolitica(usuario("f-1", "FUNCIONARIO", "dep-1"), politica));
    }

    @Test
    void puedeIniciarPolitica_debePermitirAdminYFuncionarioEnInternaSinDepartamento() {
        PoliticaNegocio politica = politicaConTipo("pol-int-any", TipoPolitica.INTERNA, null);

        assertTrue(service.puedeIniciarPolitica(usuario("a-1", "ADMIN", null), politica));
        assertTrue(service.puedeIniciarPolitica(usuario("f-1", "FUNCIONARIO", "dep-1"), politica));
        assertTrue(!service.puedeIniciarPolitica(usuario("u-1", "USUARIO", null), politica));
    }

    @Test
    void puedeIniciarPolitica_debePermitirSoloMismoDepartamentoEnInternaConDepartamento() {
        PoliticaNegocio politica = politicaConTipo("pol-int-dep", TipoPolitica.INTERNA, "dep-1");

        assertTrue(service.puedeIniciarPolitica(usuario("u-1", "USUARIO", "dep-1"), politica), "usuario mismo departamento");
        assertTrue(service.puedeIniciarPolitica(usuario("f-1", "FUNCIONARIO", "dep-1"), politica), "funcionario mismo departamento");
        assertTrue(!service.puedeIniciarPolitica(usuario("u-2", "USUARIO", "dep-2"), politica), "usuario otro departamento");
        assertFalse(service.puedeIniciarPolitica(usuario("a-1", "ADMIN", null), politica), "admin sin departamento");
    }

    @Test
    void puedeIniciarPolitica_debePermitirTodosEnAmbas() {
        PoliticaNegocio politica = politicaConTipo("pol-amb", TipoPolitica.AMBAS, null);

        assertTrue(service.puedeIniciarPolitica(usuario("u-1", "USUARIO", null), politica));
        assertTrue(service.puedeIniciarPolitica(usuario("a-1", "ADMIN", null), politica));
        assertTrue(service.puedeIniciarPolitica(usuario("f-1", "FUNCIONARIO", "dep-1"), politica));
    }

    @Test
    void crearPolitica_debeValidarMontoCuandoRequierePago() {
        CreatePoliticaRequest request = new CreatePoliticaRequest();
        request.setNombre("Politica pagada");
        request.setRequierePago(true);
        request.setMontoPago(BigDecimal.ZERO);

        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));

        ApiException ex = assertThrows(ApiException.class,
                () -> service.crearPolitica("admin-1", request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("montoPago"));
    }

    @Test
    void crearPolitica_debeAsignarUsdPorDefectoCuandoRequierePago() {
        CreatePoliticaRequest request = new CreatePoliticaRequest();
        request.setNombre("Politica pagada");
        request.setDescripcion("Desc");
        request.setRequierePago(true);
        request.setMontoPago(new BigDecimal("10.00"));

        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.save(any(PoliticaNegocio.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PoliticaNegocio politica = service.crearPolitica("admin-1", request);

        assertTrue(Boolean.TRUE.equals(politica.getRequierePago()));
        assertEquals("USD", politica.getMonedaPago());
        assertEquals(0, new BigDecimal("10").compareTo(politica.getMontoPago()));
    }

    private void prepararEscenarioBaseEliminacion(PoliticaNegocio politica) {
        when(usuarioRepository.findById("admin-1")).thenReturn(Optional.of(admin()));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(presenciaService.tieneActividadActiva("pol-1")).thenReturn(false);
        when(eventoRepository.existsByPoliticaId("pol-1")).thenReturn(false);
        when(snapshotRepository.existsByPoliticaId("pol-1")).thenReturn(false);
        when(mongoTemplate.getCollectionNames()).thenReturn(Set.of("politicas_negocio"));
    }

    private Usuario admin() {
        return Usuario.builder()
                .id("admin-1")
                .rol("ADMIN")
                .build();
    }

    private Usuario usuario(String id, String rol, String departamentoId) {
        return Usuario.builder()
                .id(id)
                .rol(rol)
                .departamentoId(departamentoId)
                .activo(true)
                .build();
    }

    private PoliticaNegocio politica(String id, EstadoPolitica estado) {
        return PoliticaNegocio.builder()
                .id(id)
                .nombre("Politica prueba")
                .estado(estado)
                .fueActivada(false)
                .secuenciaColaboracion(0L)
                .build();
    }

    private PoliticaNegocio politicaConTipo(String id, TipoPolitica tipoPolitica, String departamentoInicioId) {
        return PoliticaNegocio.builder()
                .id(id)
                .nombre("Politica prueba")
                .estado(EstadoPolitica.ACTIVA)
                .tipoPolitica(tipoPolitica)
                .departamentoInicioId(departamentoInicioId)
                .fueActivada(true)
                .secuenciaColaboracion(0L)
                .build();
    }
}
