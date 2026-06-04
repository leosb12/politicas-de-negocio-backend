package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoPermisoService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.service.InstanciaPoliticaService;
import com.leo.politicas_de_negocio.tareas.repository.TareaActividadRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentoColaborativoControllerTest {

    @Mock
    private InstanciaPoliticaService instanciaPoliticaService;

    @Mock
    private DocumentoColaborativoMetadataService metadataService;

    @Mock
    private DocumentoColaborativoPermisoService permisoService;

    @Mock
    private TareaActividadRepository tareaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private DocumentoColaborativoController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new DocumentoColaborativoController(
                instanciaPoliticaService,
                metadataService,
                permisoService,
                tareaRepository,
                usuarioRepository
        );
    }

    @Test
    void listarDocumentosColaborativos_filtraPorPermisoLecturaYAdjuntaPermisos() {
        Usuario actor = Usuario.builder()
                .id("user-externo")
                .rol("FUNCIONARIO")
                .departamentoId("dep-externo")
                .activo(true)
                .build();
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("cliente-1")
                .build();
        DocumentoColaborativoMetadata visible = documento("doc-visible", "dc");
        DocumentoColaborativoMetadata oculto = documento("doc-oculto", "contrato");
        DocumentoColaborativoPermisosDto permisosVisibles = DocumentoColaborativoPermisosDto.builder()
                .puedeLeer(true)
                .puedeEditar(false)
                .build();

        when(instanciaPoliticaService.obtenerInstanciaParaDocumentoColaborativo("inst-1", "user-externo"))
                .thenReturn(instancia);
        when(usuarioRepository.findByIdAndActivo("user-externo", true))
                .thenReturn(Optional.of(actor));
        when(metadataService.listarPorTramite("cliente-1", "inst-1"))
                .thenReturn(List.of(visible, oculto));
        when(permisoService.evaluarPermisos(visible, actor, "FUNCIONARIO", "dep-externo", instancia))
                .thenReturn(permisosVisibles);
        when(permisoService.evaluarPermisos(oculto, actor, "FUNCIONARIO", "dep-externo", instancia))
                .thenReturn(DocumentoColaborativoPermisosDto.builder().puedeLeer(false).build());

        ResponseEntity<List<DocumentoColaborativoMetadata>> response =
                controller.listarDocumentosColaborativos("inst-1", "user-externo", null);

        List<DocumentoColaborativoMetadata> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals("doc-visible", body.get(0).getDocumentoId());
        assertNotNull(body.get(0).getPermisosUsuario());
        assertFalse(body.get(0).getPermisosUsuario().isPuedeEditar());
    }

    @Test
    void listarDocumentosColaborativos_inicializaDiferidoSiNoHayMetadata() {
        Usuario actor = Usuario.builder()
                .id("user-externo")
                .rol("FUNCIONARIO")
                .departamentoId("dep-externo")
                .activo(true)
                .build();
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("inst-1")
                .creadaPor("cliente-1")
                .politicaId("pol-1")
                .build();
        DocumentoColaborativoMetadata visible = documento("doc-visible", "dc");
        DocumentoColaborativoPermisosDto permisosVisibles = DocumentoColaborativoPermisosDto.builder()
                .puedeLeer(true)
                .puedeEditar(true)
                .build();

        when(instanciaPoliticaService.obtenerInstanciaParaDocumentoColaborativo("inst-1", "user-externo"))
                .thenReturn(instancia);
        when(usuarioRepository.findByIdAndActivo("user-externo", true))
                .thenReturn(Optional.of(actor));
        when(metadataService.listarPorTramite("cliente-1", "inst-1"))
                .thenReturn(List.of(), List.of(visible));
        when(permisoService.evaluarPermisos(visible, actor, "FUNCIONARIO", "dep-externo", instancia))
                .thenReturn(permisosVisibles);

        ResponseEntity<List<DocumentoColaborativoMetadata>> response =
                controller.listarDocumentosColaborativos("inst-1", "user-externo", null);

        verify(instanciaPoliticaService).asegurarDocumentosColaborativosIniciales(instancia);
        List<DocumentoColaborativoMetadata> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());
        assertEquals("doc-visible", body.get(0).getDocumentoId());
    }

    private DocumentoColaborativoMetadata documento(String id, String campoFormularioId) {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setDocumentoId(id);
        metadata.setClienteId("cliente-1");
        metadata.setTramiteId("inst-1");
        metadata.setCampoFormularioId(campoFormularioId);
        metadata.setNombreDocumento(campoFormularioId);
        metadata.setTipoDocumento("WORD");
        metadata.setEstado("CREADO");
        return metadata;
    }
}
