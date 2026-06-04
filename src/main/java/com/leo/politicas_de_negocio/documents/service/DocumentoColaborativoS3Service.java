package com.leo.politicas_de_negocio.documents.service;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Service
public class DocumentoColaborativoS3Service {

    private static final Logger log = LoggerFactory.getLogger(DocumentoColaborativoS3Service.class);

    private final S3Client s3Client;
    private final String bucket;

    public DocumentoColaborativoS3Service(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String subirDocumentoColaborativoVacio(String clienteId, String tramiteId, String documentoId, String tipoDocumento) throws IOException {
        String extension = obtenerExtension(tipoDocumento);
        if (extension == null) {
            log.warn("Tipo de documento colaborativo no soportado para subir a S3: {}", tipoDocumento);
            return null;
        }

        String s3Key = "document-repositories/" + clienteId + "/tramites/" + tramiteId + "/documentos-colaborativos/" + documentoId + "." + extension;
        byte[] content = crearArchivoVacio(tipoDocumento, tramiteId);
        String contentType = obtenerContentType(extension);

        log.info("Subiendo documento colaborativo vacío a S3: bucket={}, key={}, contentType={}, size={}", bucket, s3Key, contentType, content.length);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromBytes(content));
        log.info("Documento colaborativo subido exitosamente a S3: {}", s3Key);

        return s3Key;
    }

    private String obtenerExtension(String tipoDocumento) {
        if ("WORD".equalsIgnoreCase(tipoDocumento)) {
            return "docx";
        } else if ("EXCEL".equalsIgnoreCase(tipoDocumento)) {
            return "xlsx";
        } else if ("POWERPOINT".equalsIgnoreCase(tipoDocumento)) {
            return "pptx";
        }
        return null;
    }

    private String obtenerContentType(String extension) {
        switch (extension) {
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default:
                return "application/octet-stream";
        }
    }

    private byte[] crearArchivoVacio(String tipoDocumento, String tramiteId) throws IOException {
        if ("WORD".equalsIgnoreCase(tipoDocumento)) {
            return crearWordVacio(tramiteId);
        } else if ("EXCEL".equalsIgnoreCase(tipoDocumento)) {
            return crearExcelVacio();
        } else if ("POWERPOINT".equalsIgnoreCase(tipoDocumento)) {
            return crearPowerPointVacio();
        }
        throw new IllegalArgumentException("Tipo de documento no soportado: " + tipoDocumento);
    }

    private byte[] crearWordVacio(String tramiteId) throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph p = doc.createParagraph();
            XWPFRun r = p.createRun();
            r.setText("Documento colaborativo creado automáticamente para el trámite " + tramiteId + ".");
            doc.write(out);
            return out.toByteArray();
        }
    }

    private byte[] crearExcelVacio() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.createSheet("Documento colaborativo");
            wb.write(out);
            return out.toByteArray();
        }
    }

    private byte[] crearPowerPointVacio() throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ppt.createSlide();
            ppt.write(out);
            return out.toByteArray();
        }
    }

    public byte[] descargarArchivo(String s3Key) {
        try {
            software.amazon.awssdk.services.s3.model.GetObjectRequest getRequest = software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build();
            return s3Client.getObject(getRequest, software.amazon.awssdk.core.sync.ResponseTransformer.toBytes()).asByteArray();
        } catch (Exception e) {
            log.error("Error al descargar archivo de S3: key={}", s3Key, e);
            throw new RuntimeException("Error al descargar archivo de S3", e);
        }
    }

    public void subirArchivo(String s3Key, byte[] content, String contentType) {
        try {
            log.info("Subiendo archivo a S3: bucket={}, key={}, contentType={}, size={}", bucket, s3Key, contentType, content.length);
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromBytes(content));
        } catch (Exception e) {
            log.error("Error al subir archivo a S3: key={}", s3Key, e);
            throw new RuntimeException("Error al subir archivo a S3", e);
        }
    }
}
