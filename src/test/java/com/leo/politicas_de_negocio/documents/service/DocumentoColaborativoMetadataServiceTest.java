package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.politicas.model.politica.ConfiguracionDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosAdicionalesDocumento;
import com.leo.politicas_de_negocio.politicas.model.politica.PermisosSeccion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentoColaborativoMetadataServiceTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<DocumentoColaborativoMetadata> metadataTable;

    @Mock
    private DocumentoColaborativoS3Service s3Service;

    private DocumentoColaborativoMetadataService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(enhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(metadataTable);
        service = new DocumentoColaborativoMetadataService(enhancedClient, "test-table", s3Service);
    }

    @Test
    void guardarMetadata_exito() {
        DocumentoColaborativoMetadata metadata = new DocumentoColaborativoMetadata();
        metadata.setPk("CLIENTE#123");
        metadata.setSk("TRAMITE#456#DOC_COLAB#789");

        service.guardarMetadata(metadata);

        verify(metadataTable, times(1)).putItem(metadata);
    }

    @Test
    void crearDocumentosColaborativosIniciales_conCamposColaborativos_exito() throws IOException {
        // Arrange
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        ConfiguracionDocumento configDoc = new ConfiguracionDocumento();
        configDoc.setTipoDocumento("WORD");
        configDoc.setModoColaboracion("DEPARTAMENTO");
        configDoc.setPermisosEdicion(PermisosSeccion.builder()
                .departamentos(List.of("dept-editor"))
                .usuarios(List.of("user-1"))
                .build());
        configDoc.setPermisosDescarga(PermisosSeccion.builder()
                .departamentos(List.of("dept-1"))
                .usuarios(List.of("user-extra"))
                .build());
        configDoc.setPermisosComentarios(PermisosSeccion.builder()
                .roles(List.of("FUNCIONARIO"))
                .build());
        configDoc.setPermisosAdicionales(PermisosAdicionalesDocumento.builder()
                .puedeDescargar(false)
                .puedeComentar(true)
                .puedeReemplazar(false)
                .puedeEliminar(false)
                .puedeCompartirInternamente(false)
                .build());

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("contrato_compra")
                .etiqueta("Contrato de Compra")
                .ayuda("Suba el contrato")
                .tipo(TipoCampo.DOCUMENTO_COLABORATIVO)
                .configuracionDocumento(configDoc)
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        when(s3Service.subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.docx");

        // Act
        service.crearDocumentosColaborativosIniciales(instancia, politica);

        // Assert
        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("CLIENTE#cliente-123", saved.getPk());
        assertTrue(saved.getSk().startsWith("TRAMITE#tramite-123#DOC_COLAB#"));
        assertEquals("cliente-123", saved.getClienteId());
        assertEquals("tramite-123", saved.getTramiteId());
        assertEquals("contrato_compra", saved.getCampoFormularioId());
        assertEquals("Contrato de Compra", saved.getNombreDocumento());
        assertEquals("Suba el contrato", saved.getDescripcion());
        assertEquals("WORD", saved.getTipoDocumento());
        assertEquals("CREADO", saved.getEstado());
        assertEquals("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.docx", saved.getS3Key());
        assertEquals(List.of("dept-editor"), saved.getPermisosEdicion().getDepartamentos());
        assertEquals(List.of("user-1"), saved.getPermisosEdicion().getUsuarios());
        assertEquals(List.of("dept-1"), saved.getPermisosDescarga().getDepartamentos());
        assertEquals(List.of("user-extra"), saved.getPermisosDescarga().getUsuarios());
        assertEquals(List.of("FUNCIONARIO"), saved.getPermisosComentarios().getRoles());
        assertEquals(false, saved.getPermisosAdicionales().getPuedeDescargar());
        assertEquals(true, saved.getPermisosAdicionales().getPuedeComentar());
    }

    @Test
    void crearDocumentosColaborativosIniciales_conCamposColaborativos_falloS3() throws IOException {
        // Arrange
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        ConfiguracionDocumento configDoc = new ConfiguracionDocumento();
        configDoc.setTipoDocumento("EXCEL");

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("planilla_costos")
                .tipo(TipoCampo.DOCUMENTO_COLABORATIVO)
                .configuracionDocumento(configDoc)
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        doThrow(new RuntimeException("S3 error"))
                .when(s3Service).subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString());

        // Act
        service.crearDocumentosColaborativosIniciales(instancia, politica);

        // Assert
        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("ERROR_CREACION_S3", saved.getEstado());
        assertNull(saved.getS3Key());
    }

    @Test
    void crearDocumentosColaborativosIniciales_conCamposColaborativos_tipoNoSoportado() {
        // Arrange
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        ConfiguracionDocumento configDoc = new ConfiguracionDocumento();
        configDoc.setTipoDocumento("PDF"); // PDF is NOT supported as collaborative document

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("documento_pdf")
                .tipo(TipoCampo.DOCUMENTO_COLABORATIVO)
                .configuracionDocumento(configDoc)
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        // Act
        service.crearDocumentosColaborativosIniciales(instancia, politica);

        // Assert
        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("TIPO_NO_SOPORTADO", saved.getEstado());
        assertNull(saved.getS3Key());
    }

    @Test
    void crearDocumentosColaborativosIniciales_tipoNormalizacion_exito() throws IOException {
        // Arrange
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        ConfiguracionDocumento configDoc = new ConfiguracionDocumento();
        configDoc.setTipoDocumento("WORD");

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("contrato_compra")
                .tipo("documento_colaborativo") // lowercase variant
                .configuracionDocumento(configDoc)
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        when(s3Service.subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.docx");

        // Act
        service.crearDocumentosColaborativosIniciales(instancia, politica);

        // Assert
        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("contrato_compra", saved.getCampoFormularioId());
        assertEquals("CREADO", saved.getEstado());
    }

    @Test
    void crearDocumentosColaborativosIniciales_tipoWordComoAlias_exito() throws IOException {
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("contrato_compra")
                .tipo("WORD")
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        when(s3Service.subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.docx");

        service.crearDocumentosColaborativosIniciales(instancia, politica);

        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("contrato_compra", saved.getCampoFormularioId());
        assertEquals("WORD", saved.getTipoDocumento());
        assertEquals("CREADO", saved.getEstado());
    }

    @Test
    void crearDocumentosColaborativosIniciales_tipoExcelComoAliasSinConfig_usaExcel() throws IOException {
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("planilla_costos")
                .tipo("EXCEL")
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        when(s3Service.subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.xlsx");

        service.crearDocumentosColaborativosIniciales(instancia, politica);

        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("planilla_costos", saved.getCampoFormularioId());
        assertEquals("EXCEL", saved.getTipoDocumento());
        assertEquals("CREADO", saved.getEstado());
    }

    @Test
    void crearDocumentosColaborativosIniciales_tipoNormalizacionConEspacios_exito() throws IOException {
        // Arrange
        InstanciaPolitica instancia = InstanciaPolitica.builder()
                .id("tramite-123")
                .creadaPor("cliente-123")
                .build();

        ConfiguracionDocumento configDoc = new ConfiguracionDocumento();
        configDoc.setTipoDocumento("WORD");

        CampoFormulario campoColab = CampoFormulario.builder()
                .campo("contrato_compra")
                .tipo("Documento Colaborativo") // space and capitalization variant
                .configuracionDocumento(configDoc)
                .build();

        Nodo nodo = Nodo.builder()
                .formulario(List.of(campoColab))
                .build();

        PoliticaNegocio politica = PoliticaNegocio.builder()
                .nodos(List.of(nodo))
                .build();

        when(s3Service.subirDocumentoColaborativoVacio(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("document-repositories/cliente-123/tramites/tramite-123/documentos-colaborativos/doc_xyz.docx");

        // Act
        service.crearDocumentosColaborativosIniciales(instancia, politica);

        // Assert
        ArgumentCaptor<DocumentoColaborativoMetadata> captor = ArgumentCaptor.forClass(DocumentoColaborativoMetadata.class);
        verify(metadataTable, times(1)).putItem(captor.capture());

        DocumentoColaborativoMetadata saved = captor.getValue();
        assertEquals("contrato_compra", saved.getCampoFormularioId());
        assertEquals("CREADO", saved.getEstado());
    }
}
