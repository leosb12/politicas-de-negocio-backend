package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.dto.S3UploadResult;
import com.leo.politicas_de_negocio.documents.model.DocumentoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.repository.InstanciaPoliticaRepository;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import com.leo.politicas_de_negocio.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentalServiceTest {

    @Mock
    private InstanciaPoliticaRepository instanciaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PoliticaNegocioRepository politicaRepository;

    @Mock
    private DocumentoS3Service s3Service;

    @Mock
    private DocumentoMetadataService metadataService;

    @Mock
    private DocumentRepositoryService repositoryService;

    private AutoCloseable mocks;
    private DocumentalService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new DocumentalService(
                instanciaRepository,
                usuarioRepository,
                politicaRepository,
                s3Service,
                metadataService,
                repositoryService
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void subirDocumento_exito() {
        // Arrange
        String actorUserId = "actor-1";
        String tramiteId = "tramite-1";
        String clienteId = "cliente-1";
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "Hello World".getBytes());

        InstanciaPolitica tramite = InstanciaPolitica.builder()
                .id(tramiteId)
                .creadaPor(clienteId)
                .codigoTramite("TRM-100")
                .politicaId("pol-1")
                .build();

        Usuario cliente = Usuario.builder()
                .id(clienteId)
                .nombre("Cliente Demo")
                .build();

        Usuario actor = Usuario.builder()
                .id(actorUserId)
                .nombre("Funcionario Demo")
                .rol("FUNCIONARIO")
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Politica de Prueba")
                .build();

        S3UploadResult s3Result = S3UploadResult.builder()
                .bucket("politicas-document-repositories-leonardo")
                .s3Key("document-repositories/cliente-1/tramite-1/doc_unique_test.pdf")
                .s3Uri("s3://politicas-document-repositories-leonardo/key")
                .s3Url("https://politicas-document-repositories-leonardo.s3.sa-east-1.amazonaws.com/key")
                .eTag("etag-123")
                .nombreArchivoSanitizado("test.pdf")
                .build();

        when(instanciaRepository.findById(tramiteId)).thenReturn(Optional.of(tramite));
        when(usuarioRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(repositoryService.obtenerORegistrarRepositoryId(clienteId, tramiteId, "pol-1")).thenReturn("repo-123");
        when(s3Service.subirArchivo(eq(clienteId), eq(tramiteId), anyString(), eq(file))).thenReturn(s3Result);

        // Act
        DocumentoMetadata metadata = service.subirDocumento(actorUserId, tramiteId, file, "WEB");

        // Assert
        assertNotNull(metadata);
        assertEquals("CLIENTE#cliente-1", metadata.getPk());
        assertEquals("repo-123", metadata.getRepositoryId());
        assertEquals("cliente-1", metadata.getClienteId());
        assertEquals("Cliente Demo", metadata.getClienteNombre());
        assertEquals(tramiteId, metadata.getTramiteId());
        assertEquals("Politica de Prueba", metadata.getTramiteNombre());
        assertEquals("TRM-100", metadata.getTramiteCodigo());
        assertEquals("test.pdf", metadata.getNombreArchivoOriginal());
        assertEquals("test.pdf", metadata.getNombreArchivoSanitizado());
        assertEquals("application/pdf", metadata.getTipoArchivo());
        assertEquals("pdf", metadata.getExtension());
        assertEquals("etag-123", metadata.getChecksum());
        assertEquals("WEB", metadata.getOrigenCarga());
        assertEquals("Funcionario Demo", metadata.getSubidoPorNombre());
        assertEquals("FUNCIONARIO", metadata.getSubidoPorRol());

        verify(metadataService, times(1)).guardarMetadata(any(DocumentoMetadata.class));
    }

    @Test
    void subirDocumento_falloDynamoDbRollbackS3() {
        // Arrange
        String actorUserId = "actor-1";
        String tramiteId = "tramite-1";
        String clienteId = "cliente-1";
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", "Hello World".getBytes());

        InstanciaPolitica tramite = InstanciaPolitica.builder()
                .id(tramiteId)
                .creadaPor(clienteId)
                .codigoTramite("TRM-100")
                .politicaId("pol-1")
                .build();

        Usuario cliente = Usuario.builder()
                .id(clienteId)
                .nombre("Cliente Demo")
                .build();

        Usuario actor = Usuario.builder()
                .id(actorUserId)
                .nombre("Funcionario Demo")
                .rol("FUNCIONARIO")
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .id("pol-1")
                .nombre("Politica de Prueba")
                .build();

        S3UploadResult s3Result = S3UploadResult.builder()
                .bucket("politicas-document-repositories-leonardo")
                .s3Key("document-repositories/cliente-1/tramite-1/doc_unique_test.pdf")
                .s3Uri("s3://politicas-document-repositories-leonardo/key")
                .s3Url("https://politicas-document-repositories-leonardo.s3.sa-east-1.amazonaws.com/key")
                .eTag("etag-123")
                .nombreArchivoSanitizado("test.pdf")
                .build();

        when(instanciaRepository.findById(tramiteId)).thenReturn(Optional.of(tramite));
        when(usuarioRepository.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(usuarioRepository.findById(actorUserId)).thenReturn(Optional.of(actor));
        when(politicaRepository.findById("pol-1")).thenReturn(Optional.of(politica));
        when(repositoryService.obtenerORegistrarRepositoryId(clienteId, tramiteId, "pol-1")).thenReturn("repo-123");
        when(s3Service.subirArchivo(eq(clienteId), eq(tramiteId), anyString(), eq(file))).thenReturn(s3Result);

        // Simulate DynamoDB write failure
        doThrow(new RuntimeException("DynamoDB error")).when(metadataService).guardarMetadata(any(DocumentoMetadata.class));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> service.subirDocumento(actorUserId, tramiteId, file, "WEB"));

        // Verify rollback was performed
        verify(s3Service, times(1)).eliminarArchivo("document-repositories/cliente-1/tramite-1/doc_unique_test.pdf");
    }
}
