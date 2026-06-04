package com.leo.politicas_de_negocio.documents.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentoColaborativoS3ServiceTest {

    @Mock
    private S3Client s3Client;

    private DocumentoColaborativoS3Service service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new DocumentoColaborativoS3Service(s3Client, "test-bucket");
    }

    @Test
    void subirDocumentoColaborativoVacio_docx_exito() throws IOException {
        // Act
        String s3Key = service.subirDocumentoColaborativoVacio("cli-1", "trm-1", "doc-1", "WORD");

        // Assert
        assertEquals("document-repositories/cli-1/tramites/trm-1/documentos-colaborativos/doc-1.docx", s3Key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);

        verify(s3Client, times(1)).putObject(requestCaptor.capture(), bodyCaptor.capture());

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("test-bucket", capturedRequest.bucket());
        assertEquals("document-repositories/cli-1/tramites/trm-1/documentos-colaborativos/doc-1.docx", capturedRequest.key());
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", capturedRequest.contentType());
        assertTrue(capturedRequest.contentLength() > 0);
    }

    @Test
    void subirDocumentoColaborativoVacio_xlsx_exito() throws IOException {
        // Act
        String s3Key = service.subirDocumentoColaborativoVacio("cli-1", "trm-1", "doc-2", "EXCEL");

        // Assert
        assertEquals("document-repositories/cli-1/tramites/trm-1/documentos-colaborativos/doc-2.xlsx", s3Key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", capturedRequest.contentType());
    }

    @Test
    void subirDocumentoColaborativoVacio_pptx_exito() throws IOException {
        // Act
        String s3Key = service.subirDocumentoColaborativoVacio("cli-1", "trm-1", "doc-3", "POWERPOINT");

        // Assert
        assertEquals("document-repositories/cli-1/tramites/trm-1/documentos-colaborativos/doc-3.pptx", s3Key);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest capturedRequest = requestCaptor.getValue();
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation", capturedRequest.contentType());
    }

    @Test
    void subirDocumentoColaborativoVacio_noSoportado_retornaNull() throws IOException {
        // Act
        String s3Key = service.subirDocumentoColaborativoVacio("cli-1", "trm-1", "doc-4", "PDF");

        // Assert
        assertNull(s3Key);
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
}
