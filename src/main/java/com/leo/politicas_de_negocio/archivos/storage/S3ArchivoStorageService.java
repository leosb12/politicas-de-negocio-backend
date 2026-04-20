package com.leo.politicas_de_negocio.archivos.storage;

import com.leo.politicas_de_negocio.archivos.config.StorageProperties;
import com.leo.politicas_de_negocio.archivos.exception.ArchivoNoEncontradoException;
import com.leo.politicas_de_negocio.archivos.exception.ArchivoStorageException;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoContenido;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStorageRequest;
import com.leo.politicas_de_negocio.archivos.storage.model.ArchivoStoredObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
public class S3ArchivoStorageService implements ArchivoStorageService {

    private final S3Client s3Client;
    private final String bucket;
    private final String keyPrefix;

    public S3ArchivoStorageService(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = s3Client;

        String configuredBucket = storageProperties.getS3() != null ? storageProperties.getS3().getBucket() : null;
        if (!StringUtils.hasText(configuredBucket)) {
            throw new IllegalStateException("Debe configurar app.storage.s3.bucket para usar almacenamiento S3");
        }

        this.bucket = configuredBucket.trim();
        this.keyPrefix = normalizarKeyPrefix(storageProperties.getS3().getKeyPrefix());
    }

    @Override
    public ArchivoStoredObject almacenar(ArchivoStorageRequest request) {
        validarContenido(request);

        String nombreGuardado = normalizarNombreGuardado(request.getNombreGuardado());
        String subdirectorio = normalizarSubdirectorio(request.getSubdirectorio());
        String key = construirKey(subdirectorio, nombreGuardado);

        PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength((long) request.getContenido().length);

        if (StringUtils.hasText(request.getContentType())) {
            putBuilder.contentType(request.getContentType().trim());
        }

        try {
            s3Client.putObject(putBuilder.build(), RequestBody.fromBytes(request.getContenido()));
        } catch (S3Exception ex) {
            throw new ArchivoStorageException("No se pudo guardar el archivo en S3", ex);
        }

        return ArchivoStoredObject.builder()
                .nombreGuardado(nombreGuardado)
                .rutaOKey(key)
                .storageType("s3")
                .urlAcceso(construirReferenciaAcceso(key))
                .bucket(bucket)
                .build();
    }

    @Override
    public ArchivoContenido descargar(String rutaOKey) {
        String key = normalizarKey(rutaOKey);

        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );

            return ArchivoContenido.builder()
                    .contenido(object.asByteArray())
                    .contentType(object.response().contentType())
                    .build();
        } catch (NoSuchKeyException ex) {
            throw new ArchivoNoEncontradoException("Archivo no encontrado en S3");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ArchivoNoEncontradoException("Archivo no encontrado en S3");
            }
            throw new ArchivoStorageException("No se pudo descargar el archivo desde S3", ex);
        }
    }

    @Override
    public void eliminar(String rutaOKey) {
        String key = normalizarKey(rutaOKey);

        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (NoSuchKeyException ex) {
            throw new ArchivoNoEncontradoException("Archivo no encontrado en S3");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ArchivoNoEncontradoException("Archivo no encontrado en S3");
            }
            throw new ArchivoStorageException("No se pudo validar la existencia del archivo en S3", ex);
        }

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            throw new ArchivoStorageException("No se pudo eliminar el archivo en S3", ex);
        }
    }

    @Override
    public String construirReferenciaAcceso(String rutaOKey) {
        String key = normalizarKey(rutaOKey);
        return "s3://" + bucket + "/" + key;
    }

    private void validarContenido(ArchivoStorageRequest request) {
        if (request == null || request.getContenido() == null || request.getContenido().length == 0) {
            throw new ArchivoStorageException("El contenido del archivo es obligatorio para almacenar");
        }
    }

    private String construirKey(String subdirectorio, String nombreGuardado) {
        StringBuilder builder = new StringBuilder();

        if (StringUtils.hasText(keyPrefix)) {
            builder.append(keyPrefix);
        }

        if (StringUtils.hasText(subdirectorio)) {
            builder.append(subdirectorio).append('/');
        }

        builder.append(nombreGuardado);
        return normalizarKey(builder.toString());
    }

    private String normalizarKeyPrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }

        String normalized = prefix.trim().replace("\\", "/").replaceAll("/+", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.isEmpty() && !normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }

    private String normalizarKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ArchivoStorageException("Key invalida para almacenamiento S3");
        }

        String normalized = key.trim().replace("\\", "/").replaceAll("/+", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new ArchivoStorageException("Key invalida para almacenamiento S3");
        }

        return normalized;
    }

    private String normalizarSubdirectorio(String subdirectorio) {
        if (!StringUtils.hasText(subdirectorio)) {
            return "";
        }

        String normalized = subdirectorio.trim().replace("\\", "/").replaceAll("/+", "/");
        String[] segmentos = normalized.split("/");
        List<String> limpios = new ArrayList<>();

        for (String segmento : segmentos) {
            if (!StringUtils.hasText(segmento)) {
                continue;
            }
            String cleaned = segmento.trim();
            if (".".equals(cleaned) || "..".equals(cleaned)) {
                continue;
            }
            cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (StringUtils.hasText(cleaned)) {
                limpios.add(cleaned);
            }
        }

        return String.join("/", limpios);
    }

    private String normalizarNombreGuardado(String nombreGuardado) {
        if (!StringUtils.hasText(nombreGuardado)) {
            throw new ArchivoStorageException("El nombre guardado del archivo es invalido");
        }

        String cleaned = nombreGuardado.trim().replace("\\", "/");
        int lastSlashIndex = cleaned.lastIndexOf('/');
        if (lastSlashIndex >= 0) {
            cleaned = cleaned.substring(lastSlashIndex + 1);
        }

        cleaned = cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (!StringUtils.hasText(cleaned) || cleaned.contains("..")) {
            throw new ArchivoStorageException("El nombre guardado del archivo es invalido");
        }

        return cleaned;
    }
}
