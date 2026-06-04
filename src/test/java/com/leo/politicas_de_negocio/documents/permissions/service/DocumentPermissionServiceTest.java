package com.leo.politicas_de_negocio.documents.permissions.service;

import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionConfigResponse;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationRequest;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentPermissionValidationResponse;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionConfig;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionRule;
import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionSet;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentPermissionAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentSubjectType;
import com.leo.politicas_de_negocio.documents.permissions.repository.DocumentPermissionConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DocumentPermissionServiceTest {

    private DocumentPermissionConfigRepository repository;
    private DocumentPermissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentPermissionConfigRepository.class);
        service = new DocumentPermissionService(repository);
    }

    @Test
    void crearConfiguracionPermisos_debeCargarReglasYAuditoriaPorDefecto() {
        when(repository.existsByCampoIdAndActivoTrue("campo-doc")).thenReturn(false);
        when(repository.save(any(DocumentPermissionConfig.class))).thenAnswer(invocation -> {
            DocumentPermissionConfig config = invocation.getArgument(0);
            config.setId("cfg-1");
            return config;
        });

        DocumentPermissionConfigRequest request = new DocumentPermissionConfigRequest();
        request.setCampoId("campo-doc");
        request.setCampoNombre("Documento de respaldo");

        DocumentPermissionConfigResponse response = service.crearConfiguracionPermisos("admin-1", request);

        assertEquals("cfg-1", response.getId());
        assertEquals("campo-doc", response.getCampoId());
        assertEquals(4, response.getReglasPermiso().size());
        assertTrue(response.getReglasPermiso().stream().anyMatch(rule -> "CLIENTE".equals(rule.getSujetoId())));
        assertTrue(response.getReglasPermiso().stream().anyMatch(rule -> "ADMINISTRADOR".equals(rule.getSujetoId())
                && Boolean.TRUE.equals(rule.getPermisos().getAdministrarPermisos())));
        assertNotNull(response.getAuditoria());
        assertTrue(response.getAuditoria().getAuditarDescarga());
    }

    @Test
    void validarPermiso_debePriorizarUsuarioSobreRol() {
        DocumentPermissionConfig config = DocumentPermissionConfig.builder()
                .id("cfg-1")
                .campoId("campo-doc")
                .activo(true)
                .reglasPermiso(List.of(
                        DocumentPermissionRule.builder()
                                .tipoSujeto(DocumentSubjectType.ROL)
                                .sujetoId("ADMINISTRADOR")
                                .sujetoNombre("Administrador")
                                .permisos(DocumentPermissionSet.builder().eliminar(true).build())
                                .aplicaDesde(LocalDateTime.now().minusDays(1))
                                .activo(true)
                                .build(),
                        DocumentPermissionRule.builder()
                                .tipoSujeto(DocumentSubjectType.USUARIO)
                                .sujetoId("user-1")
                                .sujetoNombre("Usuario restringido")
                                .permisos(DocumentPermissionSet.builder().eliminar(false).build())
                                .aplicaDesde(LocalDateTime.now().minusDays(1))
                                .activo(true)
                                .build()
                ))
                .build();
        when(repository.findByCampoIdAndActivoTrue("campo-doc")).thenReturn(Optional.of(config));

        DocumentPermissionValidationRequest request = new DocumentPermissionValidationRequest();
        request.setCampoId("campo-doc");
        request.setUsuarioId("user-1");
        request.setRol("ADMIN");
        request.setAccion(DocumentPermissionAction.ELIMINAR);

        DocumentPermissionValidationResponse response = service.validarPermiso(request);

        assertFalse(response.getPermitido());
        assertEquals("USUARIO", response.getReglaAplicadaTipo());
        assertEquals("user-1", response.getReglaAplicadaSujetoId());
    }

    @Test
    void validarPermiso_debePermitirSubidaAClienteIniciadorPorRolUsuarioSinClienteId() {
        DocumentPermissionConfig config = DocumentPermissionConfig.builder()
                .id("cfg-1")
                .campoId("documento")
                .activo(true)
                .reglasPermiso(List.of(
                        DocumentPermissionRule.builder()
                                .tipoSujeto(DocumentSubjectType.CLIENTE)
                                .sujetoId(DocumentPermissionService.CLIENTE_INICIADOR_SUJETO_ID)
                                .sujetoNombre("Cliente que inicio el tramite")
                                .permisos(DocumentPermissionSet.builder().subir(true).build())
                                .aplicaDesde(LocalDateTime.now().minusDays(1))
                                .activo(true)
                                .build()
                ))
                .build();
        when(repository.findByCampoIdAndActivoTrue("documento")).thenReturn(Optional.of(config));

        DocumentPermissionValidationRequest request = new DocumentPermissionValidationRequest();
        request.setCampoId("documento");
        request.setUsuarioId("cliente-1");
        request.setRol("USUARIO");
        request.setAccion(DocumentPermissionAction.SUBIR);

        DocumentPermissionValidationResponse response = service.validarPermiso(request);

        assertTrue(response.getPermitido());
        assertEquals("CLIENTE", response.getReglaAplicadaTipo());
        assertEquals(DocumentPermissionService.CLIENTE_INICIADOR_SUJETO_ID, response.getReglaAplicadaSujetoId());
    }

    @Test
    void validarPermiso_noDebeDescartarReglaActivaPorAplicaDesdeFuturoDelFrontend() {
        DocumentPermissionConfig config = DocumentPermissionConfig.builder()
                .id("cfg-1")
                .campoId("documento")
                .activo(true)
                .reglasPermiso(List.of(
                        DocumentPermissionRule.builder()
                                .tipoSujeto(DocumentSubjectType.CLIENTE)
                                .sujetoId(DocumentPermissionService.CLIENTE_INICIADOR_SUJETO_ID)
                                .sujetoNombre("Cliente que inicio el tramite")
                                .permisos(DocumentPermissionSet.builder().subir(true).build())
                                .aplicaDesde(LocalDateTime.now().plusHours(4))
                                .activo(true)
                                .build()
                ))
                .build();
        when(repository.findByCampoIdAndActivoTrue("documento")).thenReturn(Optional.of(config));

        DocumentPermissionValidationRequest request = new DocumentPermissionValidationRequest();
        request.setCampoId("documento");
        request.setUsuarioId("cliente-1");
        request.setRol("USUARIO");
        request.setAccion(DocumentPermissionAction.SUBIR);

        DocumentPermissionValidationResponse response = service.validarPermiso(request);

        assertTrue(response.getPermitido());
        assertEquals("CLIENTE", response.getReglaAplicadaTipo());
    }
}
