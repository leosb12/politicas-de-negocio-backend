package com.leo.politicas_de_negocio.documents.controller;

import com.leo.politicas_de_negocio.documents.dto.DocumentoColaborativoPermisosDto;
import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.permissions.dto.DocumentAuditEventRequest;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditAction;
import com.leo.politicas_de_negocio.documents.permissions.model.enums.DocumentAuditResult;
import com.leo.politicas_de_negocio.documents.permissions.service.DocumentAuditService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoMetadataService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoPermisoService;
import com.leo.politicas_de_negocio.documents.service.DocumentoColaborativoS3Service;
import com.leo.politicas_de_negocio.documents.service.DocumentoVersionService;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentoColaborativoEditorControllerTest {

    @Mock
    private DocumentoColaborativoMetadataService metadataService;

    @Mock
    private DocumentoColaborativoPermisoService permisoService;

    @Mock
    private DocumentoColaborativoS3Service s3Service;

    @Mock
    private DocumentoVersionService versionService;

    @Mock
    private DocumentAuditService auditService;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private InstanciaPoliticaRepository instanciaPoliticaRepository;

    private DocumentoColaborativoEditorController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new DocumentoColaborativoEditorController(
                metadataService,
                permisoService,
                s3Service,
                versionService,
                auditService,
                usuarioRepository,
                instanciaPoliticaRepository
        );
    }

    @Test
    void obtenerEditorConfig_registraVisualizacionEnAuditoria() {
        Usuario usuario = Usuario.builder()
                .id("user-1")
                .nombre("Ana")
                .rol("FUNCIONARIO")
                .departamentoId("dep-1")
                .build();
        DocumentoColaborativoMetadata metadata = documento();
        metadata.setAuditarCambios(true);
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-1")
                .politicaId("politica-1")
                .build();
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        when(usuarioRepository.findById("user-1")).thenReturn(Optional.of(usuario));
        when(metadataService.buscarPorDocumentoId("doc-1")).thenReturn(metadata);
        when(instanciaPoliticaRepository.findById("tramite-1")).thenReturn(Optional.of(instancia));
        when(permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", "dep-1", instancia))
                .thenReturn(DocumentoColaborativoPermisosDto.builder()
                        .puedeLeer(true)
                        .puedeEditar(false)
                        .build());
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

        ResponseEntity<Map<String, Object>> response =
                controller.obtenerEditorConfig("doc-1", "user-1", null, "JUnit", servletRequest);

        ArgumentCaptor<DocumentAuditEventRequest> captor = ArgumentCaptor.forClass(DocumentAuditEventRequest.class);
        verify(auditService).registrarEventoAuditoria(captor.capture());
        DocumentAuditEventRequest request = captor.getValue();

        assertEquals("doc-1", request.getDocumentoId());
        assertEquals("campo-1", request.getCampoId());
        assertEquals("tramite-1", request.getTramiteId());
        assertEquals("cliente-1", request.getClienteId());
        assertEquals("politica-1", request.getPoliticaId());
        assertEquals("nodo-1", request.getNodoId());
        assertEquals(DocumentAuditAction.VISUALIZAR, request.getAccion());
        assertEquals("user-1", request.getUsuarioId());
        assertEquals("Ana", request.getUsuarioNombre());
        assertEquals("10.0.0.1", request.getIp());
        assertEquals("JUnit", request.getUserAgent());
        assertEquals(DocumentAuditResult.PERMITIDO, request.getResultado());

        Map<String, Object> body = response.getBody();
        Map<String, Object> config = (Map<String, Object>) body.get("config");
        Map<String, Object> editorConfig = (Map<String, Object>) config.get("editorConfig");
        Map<String, Object> document = (Map<String, Object>) config.get("document");
        Map<String, Object> permissions = (Map<String, Object>) document.get("permissions");
        Map<String, Object> customization = (Map<String, Object>) editorConfig.get("customization");
        Map<String, Object> review = (Map<String, Object>) customization.get("review");
        Map<String, Object> audit = (Map<String, Object>) body.get("audit");
        assertEquals(Boolean.TRUE, permissions.get("review"));
        assertEquals(Boolean.TRUE, customization.get("forcesave"));
        assertEquals(Boolean.TRUE, review.get("trackChanges"));
        assertEquals("markup", review.get("reviewDisplay"));
        assertEquals("EDITAR", audit.get("editAction"));
        assertEquals("onDocumentStateChange", audit.get("editEvent"));
    }

    @Test
    void obtenerArchivoFuente_registraVisualizacionEnAuditoriaCuandoRecibeUsuario() {
        Usuario usuario = Usuario.builder()
                .id("user-1")
                .nombre("Ana")
                .rol("FUNCIONARIO")
                .departamentoId("dep-1")
                .build();
        DocumentoColaborativoMetadata metadata = documento();
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-1")
                .politicaId("politica-1")
                .build();
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        when(metadataService.buscarPorDocumentoId("doc-1")).thenReturn(metadata);
        when(usuarioRepository.findById("user-1")).thenReturn(Optional.of(usuario));
        when(instanciaPoliticaRepository.findById("tramite-1")).thenReturn(Optional.of(instancia));
        when(permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", "dep-1", instancia))
                .thenReturn(DocumentoColaborativoPermisosDto.builder()
                        .puedeLeer(true)
                        .build());
        when(s3Service.descargarArchivo("documentos/contrato.docx")).thenReturn(new byte[] {1, 2, 3});
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");

        ResponseEntity<byte[]> response = controller.obtenerArchivoFuente(
                "doc-1", null, null, "OnlyOffice", "user-1", null, servletRequest);

        assertEquals(3, response.getBody().length);
        ArgumentCaptor<DocumentAuditEventRequest> captor = ArgumentCaptor.forClass(DocumentAuditEventRequest.class);
        verify(auditService).registrarEventoAuditoria(captor.capture());
        DocumentAuditEventRequest request = captor.getValue();

        assertEquals("doc-1", request.getDocumentoId());
        assertEquals(DocumentAuditAction.VISUALIZAR, request.getAccion());
        assertEquals("user-1", request.getUsuarioId());
        assertEquals("127.0.0.1", request.getIp());
        assertEquals("OnlyOffice", request.getUserAgent());
        assertEquals(DocumentAuditResult.PERMITIDO, request.getResultado());
    }

    @Test
    void registrarEventoOnlyOffice_registraEdicionDescargaEImpresionConPermisos() {
        Usuario usuario = Usuario.builder()
                .id("user-1")
                .nombre("Ana")
                .rol("FUNCIONARIO")
                .departamentoId("dep-1")
                .build();
        DocumentoColaborativoMetadata metadata = documento();
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-1")
                .politicaId("politica-1")
                .build();
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        when(usuarioRepository.findById("user-1")).thenReturn(Optional.of(usuario));
        when(metadataService.buscarPorDocumentoId("doc-1")).thenReturn(metadata);
        when(instanciaPoliticaRepository.findById("tramite-1")).thenReturn(Optional.of(instancia));
        when(permisoService.evaluarPermisos(metadata, usuario, "FUNCIONARIO", "dep-1", instancia))
                .thenReturn(DocumentoColaborativoPermisosDto.builder()
                        .puedeLeer(true)
                        .puedeEditar(true)
                        .puedeDescargar(true)
                        .puedeImprimir(true)
                        .build());

        controller.registrarEventoOnlyOffice(
                "doc-1", "user-1", null, "JUnit", Map.of("accion", "EDITAR"), servletRequest);
        controller.registrarEventoOnlyOffice(
                "doc-1", "user-1", null, "JUnit", Map.of("accion", "DESCARGAR"), servletRequest);
        controller.registrarEventoOnlyOffice(
                "doc-1", "user-1", null, "JUnit", Map.of("accion", "IMPRIMIR"), servletRequest);

        ArgumentCaptor<DocumentAuditEventRequest> captor = ArgumentCaptor.forClass(DocumentAuditEventRequest.class);
        verify(auditService, times(3)).registrarEventoAuditoria(captor.capture());

        assertEquals(DocumentAuditAction.EDITAR, captor.getAllValues().get(0).getAccion());
        assertEquals(DocumentAuditAction.DESCARGAR, captor.getAllValues().get(1).getAccion());
        assertEquals(DocumentAuditAction.IMPRIMIR, captor.getAllValues().get(2).getAccion());
        assertEquals(DocumentAuditResult.PERMITIDO, captor.getAllValues().get(0).getResultado());
        assertEquals(DocumentAuditResult.PERMITIDO, captor.getAllValues().get(1).getResultado());
        assertEquals(DocumentAuditResult.PERMITIDO, captor.getAllValues().get(2).getResultado());
    }

    private DocumentoColaborativoMetadata documento() {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setDocumentoId("doc-1");
        metadata.setClienteId("cliente-1");
        metadata.setTramiteId("tramite-1");
        metadata.setCampoFormularioId("campo-1");
        metadata.setNodoId("nodo-1");
        metadata.setNombreDocumento("Contrato");
        metadata.setTipoDocumento("WORD");
        metadata.setEstado("CREADO");
        metadata.setS3Key("documentos/contrato.docx");
        return metadata;
    }
}
