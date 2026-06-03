package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.dto.S3UploadResult;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;

@Service
public class DocumentoS3Service {

    private static final Logger log = LoggerFactory.getLogger(DocumentoS3Service.class);

    private final S3Client s3Client;
    private final String bucket;
    private final String region;

    public DocumentoS3Service(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket,
            @Value("${aws.region}") String region) {
        this.s3Client = s3Client;
        this.bucket = bucket;
        this.region = region;
    }

    public S3UploadResult subirArchivo(String clienteId, String tramiteId, String archivoId, MultipartFile file) {
        log.info("Iniciando subida a S3: clienteId={}, tramiteId={}, archivoId={}", clienteId, tramiteId, archivoId);

        String originalFilename = file.getOriginalFilename();
        String nombreSanitizado = sanitizarNombre(originalFilename);
        String s3Key = "document-repositories/" + clienteId + "/" + tramiteId + "/" + archivoId + "_" + nombreSanitizado;

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();

            RequestBody requestBody = RequestBody.fromInputStream(file.getInputStream(), file.getSize());
            PutObjectResponse response = s3Client.putObject(putObjectRequest, requestBody);

            String s3Uri = "s3://" + bucket + "/" + s3Key;
            String s3Url = "https://" + bucket + ".s3." + region + ".amazonaws.com/" + s3Key;
            String eTag = response.eTag();

            log.info("Subida a S3 exitosa: s3Key={}, eTag={}", s3Key, eTag);

            return S3UploadResult.builder()
                    .bucket(bucket)
                    .s3Key(s3Key)
                    .s3Uri(s3Uri)
                    .s3Url(s3Url)
                    .eTag(eTag)
                    .nombreArchivoSanitizado(nombreSanitizado)
                    .build();

        } catch (IOException e) {
            log.error("Error al leer el archivo para subir a S3", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer el archivo físico para su almacenamiento");
        } catch (Exception e) {
            log.error("Error al subir archivo a S3", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error de comunicación con AWS S3: " + e.getMessage());
        }
    }

    public void eliminarArchivo(String s3Key) {
        log.info("Eliminando archivo de S3: s3Key={}", s3Key);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(s3Key)
                    .build());
            log.info("Archivo eliminado de S3: s3Key={}", s3Key);
        } catch (Exception e) {
            log.error("Error al eliminar archivo de S3: s3Key={}", s3Key, e);
        }
    }

    private String sanitizarNombre(String original) {
        if (original == null || original.isBlank()) {
            return "archivo";
        }
        String name = original;
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        int lastBackslash = name.lastIndexOf('\\');
        if (lastBackslash >= 0) {
            name = name.substring(lastBackslash + 1);
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
