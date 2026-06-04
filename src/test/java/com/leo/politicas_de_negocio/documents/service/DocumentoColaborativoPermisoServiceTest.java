package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class DocumentoColaborativoPermisoServiceTest {

    @Mock
    private TareaActividadRepository tareaActividadRepository;

    private DocumentoColaborativoPermisoService permisoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        permisoService = new DocumentoColaborativoPermisoService(tareaActividadRepository);
    }

    @Test
    void evaluarPermisos_usuarioEnPermisosEdicion_puedeEditar() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosEdicion permEd = new DocumentoColaborativoMetadata.PermisosEdicion();
        permEd.setUsuarios(List.of("user-123"));
        metadata.setPermisosEdicion(permEd);

        Usuario usuario = Usuario.builder().id("user-123").rol("USER").departamentoId("dept-1").build();

        // Act
        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "USER", "dept-1", null);

        // Assert
        assertTrue(result.isPuedeEditar());
        assertTrue(result.isPuedeLeer());
    }

    @Test
    void evaluarPermisos_rolEnPermisosEdicion_puedeEditar() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosEdicion permEd = new DocumentoColaborativoMetadata.PermisosEdicion();
        permEd.setRoles(List.of("ADMIN"));
        metadata.setPermisosEdicion(permEd);

        Usuario usuario = Usuario.builder().id("user-123").rol("ADMIN").departamentoId("dept-1").build();

        // Act
        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "ADMIN", "dept-1", null);

        // Assert
        assertTrue(result.isPuedeEditar());
    }

    @Test
    void evaluarPermisos_departamentoEnPermisosEdicion_puedeEditar() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosEdicion permEd = new DocumentoColaborativoMetadata.PermisosEdicion();
        permEd.setDepartamentos(List.of("dept-1"));
        metadata.setPermisosEdicion(permEd);

        Usuario usuario = Usuario.builder().id("user-123").rol("FUNCIONARIO").departamentoId("dept-1").build();

        // Act
        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", "dept-1", null);

        // Assert
        assertTrue(result.isPuedeEditar());
    }

    @Test
    void evaluarPermisos_departamentoMasUsuarioEspecifico_sumaPermisosEdicion() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosEdicion permEd = new DocumentoColaborativoMetadata.PermisosEdicion();
        permEd.setDepartamentos(List.of("dept-1"));
        permEd.setUsuarios(List.of("user-extra"));
        metadata.setPermisosEdicion(permEd);

        Usuario usuarioDepartamento = Usuario.builder().id("user-dept").rol("FUNCIONARIO").departamentoId("dept-1").build();
        Usuario usuarioEspecifico = Usuario.builder().id("user-extra").rol("FUNCIONARIO").departamentoId("dept-2").build();
        Usuario usuarioSinPermiso = Usuario.builder().id("user-out").rol("FUNCIONARIO").departamentoId("dept-2").build();

        DocumentoColaborativoPermisosDto resultDepartamento = permisoService.evaluarPermisos(metadata, usuarioDepartamento, "FUNCIONARIO", "dept-1", null);
        DocumentoColaborativoPermisosDto resultUsuario = permisoService.evaluarPermisos(metadata, usuarioEspecifico, "FUNCIONARIO", "dept-2", null);
        DocumentoColaborativoPermisosDto resultSinPermiso = permisoService.evaluarPermisos(metadata, usuarioSinPermiso, "FUNCIONARIO", "dept-2", null);

        assertTrue(resultDepartamento.isPuedeEditar());
        assertTrue(resultUsuario.isPuedeEditar());
        assertFalse(resultSinPermiso.isPuedeEditar());
    }

    @Test
    void evaluarPermisos_modoColaboracionSinChecks_noConcedeEdicion() {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setTramiteId("tramite-123");
        metadata.setCampoFormularioId("campo-abc");

        DocumentoColaborativoMetadata.ConfiguracionOrigen config = new DocumentoColaborativoMetadata.ConfiguracionOrigen();
        config.setModoColaboracion("FUNCIONARIO_RESPONSABLE");
        metadata.setConfiguracionOrigen(config);

        Usuario usuario = Usuario.builder().id("user-123").rol("FUNCIONARIO").build();
        CampoFormulario campo = CampoFormulario.builder().campo("campo-abc").build();
        TareaActividad task = TareaActividad.builder()
                .asignadoA("user-123")
                .formularioDefinicion(List.of(campo))
                .build();

        when(tareaActividadRepository.findByInstanciaIdOrderByFechaCreacionAsc("tramite-123"))
                .thenReturn(List.of(task));

        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", null, null);

        assertFalse(result.isPuedeEditar());
        assertFalse(result.isPuedeLeer());
    }

    @Test
    void evaluarPermisos_permisoLecturaParaIniciador_puedeLeerNoEditar() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setTramiteId("tramite-123");

        DocumentoColaborativoMetadata.PermisosLectura permLec = new DocumentoColaborativoMetadata.PermisosLectura();
        permLec.setIncluirClienteIniciador(true);
        metadata.setPermisosLectura(permLec);

        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .creadaPor("cliente-iniciador")
                .build();

        Usuario usuario = Usuario.builder().id("cliente-iniciador").rol("CLIENTE").build();

        // Act
        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "CLIENTE", null, instancia);

        // Assert
        assertFalse(result.isPuedeEditar());
        assertTrue(result.isPuedeLeer());
    }

    @Test
    void evaluarPermisos_adminSinChecks_noPuedeLeer() {
        // Arrange
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        Usuario usuario = Usuario.builder().id("admin-id").rol("ADMINISTRADOR").build();

        // Act
        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "ADMINISTRADOR", null, null);

        // Assert
        assertFalse(result.isPuedeLeer());
    }

    @Test
    void evaluarPermisos_permisosAdicionalesNull_sonFalse() {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosEdicion permEd = new DocumentoColaborativoMetadata.PermisosEdicion();
        permEd.setUsuarios(List.of("user-123"));
        metadata.setPermisosEdicion(permEd);

        Usuario usuario = Usuario.builder().id("user-123").rol("FUNCIONARIO").build();

        DocumentoColaborativoPermisosDto result = permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", null, null);

        assertTrue(result.isPuedeEditar());
        assertFalse(result.isPuedeDescargar());
        assertFalse(result.isPuedeComentar());
        assertFalse(result.isPuedeReemplazar());
        assertFalse(result.isPuedeEliminar());
        assertFalse(result.isPuedeCompartirInternamente());
    }

    @Test
    void evaluarPermisos_permisosDescargaPorDepartamentoYUsuario_sumaSujetos() {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        DocumentoColaborativoMetadata.PermisosAccion permisosDescarga = new DocumentoColaborativoMetadata.PermisosAccion();
        permisosDescarga.setDepartamentos(List.of("dept-1"));
        permisosDescarga.setUsuarios(List.of("user-extra"));
        metadata.setPermisosDescarga(permisosDescarga);

        Usuario usuarioDepartamento = Usuario.builder().id("user-dept").rol("FUNCIONARIO").departamentoId("dept-1").build();
        Usuario usuarioEspecifico = Usuario.builder().id("user-extra").rol("FUNCIONARIO").departamentoId("dept-2").build();
        Usuario usuarioSinPermiso = Usuario.builder().id("user-out").rol("FUNCIONARIO").departamentoId("dept-2").build();

        assertTrue(permisoService.evaluarPermisos(metadata, usuarioDepartamento, "FUNCIONARIO", "dept-1", null).isPuedeDescargar());
        assertTrue(permisoService.evaluarPermisos(metadata, usuarioEspecifico, "FUNCIONARIO", "dept-2", null).isPuedeDescargar());
        assertFalse(permisoService.evaluarPermisos(metadata, usuarioSinPermiso, "FUNCIONARIO", "dept-2", null).isPuedeDescargar());
    }
}
