package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentoColaborativoMetadata;
import com.leo.politicas_de_negocio.documents.model.DocumentoVersion;
import com.leo.politicas_de_negocio.usuarios.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DocumentoVersionService {

    private static final String TIPO_REGISTRO_VERSION = "VERSION";

    private final DynamoDbTable<DocumentoVersion> versionTable;
    private final DocumentoColaborativoS3Service s3Service;

    public DocumentoVersionService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.document-repositories-table}") String tableName,
            DocumentoColaborativoS3Service s3Service) {
        this.versionTable = enhancedClient.table(tableName, TableSchema.fromBean(DocumentoVersion.class));
        this.s3Service = s3Service;
    }

    public List<DocumentoVersion> listarVersiones(DocumentoColaborativoMetadata metadata) {
        List<DocumentoVersion> versiones = new ArrayList<>();
        QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                Key.builder()
                        .partitionValue(resolverClientId(metadata))
                        .sortValue(prefijoVersion(metadata))
                        .build()
        );

        SdkIterable<Page<DocumentoVersion>> pages = versionTable.query(r -> r.queryConditional(queryConditional));
        for (Page<DocumentoVersion> page : pages) {
            for (DocumentoVersion version : page.items()) {
                if (TIPO_REGISTRO_VERSION.equalsIgnoreCase(version.getTipoRegistro())) {
                    versiones.add(version);
                }
            }
        }
        versiones.sort(Comparator.comparing(
                DocumentoVersion::getNumeroVersion,
                Comparator.nullsLast(Integer::compareTo)
        ));
        return versiones;
    }

    public Optional<DocumentoVersion> buscarVersion(DocumentoColaborativoMetadata metadata, Integer numeroVersion) {
        if (numeroVersion == null || numeroVersion < 1) {
            return Optional.empty();
        }
        Key key = Key.builder()
                .partitionValue(resolverClientId(metadata))
                .sortValue(repositoryIdVersion(metadata, numeroVersion))
                .build();
        return Optional.ofNullable(versionTable.getItem(r -> r.key(key)));
    }

    public DocumentoVersion crearVersion(
            DocumentoColaborativoMetadata metadata,
            byte[] contenido,
            String fileType,
            Usuario usuario,
            String usuarioIdFallback,
            String origen,
            String accion
    ) {
        int numeroVersion = siguienteNumeroVersion(metadata);
        String s3KeyVersion = construirS3KeyVersion(metadata.getS3Key(), numeroVersion, fileType);
        s3Service.subirArchivo(s3KeyVersion, contenido, resolverContentType(fileType));
        String checksum = hashSha256(contenido);
        String now = LocalDateTime.now().toString();
        String createdBy = usuario != null ? usuario.getId() : usuarioIdFallback;

        DocumentoVersion version = DocumentoVersion.builder()
                .clientId(resolverClientId(metadata))
                .repositoryId(repositoryIdVersion(metadata, numeroVersion))
                .clienteId(metadata.getClienteId())
                .documentoId(metadata.getDocumentoId())
                .archivoId(metadata.getDocumentoId())
                .tipoRegistro(TIPO_REGISTRO_VERSION)
                .numeroVersion(numeroVersion)
                .s3KeyVersion(s3KeyVersion)
                .s3KeyActual(metadata.getS3Key())
                .nombreArchivo(resolverNombreArchivo(metadata.getNombreDocumento(), fileType))
                .createdAt(now)
                .createdBy(createdBy)
                .creadoPorUsuarioId(createdBy)
                .creadoPorNombre(resolverNombreUsuario(usuario, usuarioIdFallback))
                .fechaCreacion(now)
                .origen(origen)
                .accion(normalizarAccion(accion, origen))
                .tamanioBytes(contenido != null ? (long) contenido.length : 0L)
                .checksum(checksum)
                .hashArchivoOpcional(checksum)
                .build();
        versionTable.putItem(version);
        return version;
    }

    private int siguienteNumeroVersion(DocumentoColaborativoMetadata metadata) {
        return listarVersiones(metadata).stream()
                .map(DocumentoVersion::getNumeroVersion)
                .filter(value -> value != null && value > 0)
                .max(Integer::compareTo)
                .map(value -> value + 1)
                .orElse(1);
    }

    private String resolverClientId(DocumentoColaborativoMetadata metadata) {
        if (metadata.getPk() != null && !metadata.getPk().isBlank()) {
            return metadata.getPk();
        }
        return "CLIENTE#" + metadata.getClienteId();
    }

    private String prefijoVersion(DocumentoColaborativoMetadata metadata) {
        return "TRAMITE#" + metadata.getTramiteId()
                + "#DOC_COLAB#" + metadata.getDocumentoId()
                + "#VERSION#";
    }

    private String repositoryIdVersion(DocumentoColaborativoMetadata metadata, int numeroVersion) {
        return prefijoVersion(metadata) + String.format("%09d", numeroVersion);
    }

    private String construirS3KeyVersion(String s3KeyActual, int numeroVersion, String fileType) {
        String extension = normalizarExtension(fileType);
        int lastSlash = s3KeyActual != null ? s3KeyActual.lastIndexOf('/') : -1;
        String base = lastSlash >= 0 ? s3KeyActual.substring(0, lastSlash) : "document-repositories";
        return base + "/versiones/v" + numeroVersion + "." + extension;
    }

    private String resolverNombreArchivo(String nombreDocumento, String fileType) {
        String extension = normalizarExtension(fileType);
        String base = nombreDocumento == null || nombreDocumento.isBlank() ? "documento" : nombreDocumento.trim();
        return base.toLowerCase().endsWith("." + extension) ? base : base + "." + extension;
    }

    private String normalizarExtension(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            return "bin";
        }
        return fileType.trim().toLowerCase();
    }

    private String resolverContentType(String fileType) {
        return switch (normalizarExtension(fileType)) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String resolverNombreUsuario(Usuario usuario, String fallback) {
        if (usuario != null) {
            if (usuario.getNombre() != null && !usuario.getNombre().isBlank()) {
                return usuario.getNombre();
            }
            if (usuario.getCorreo() != null && !usuario.getCorreo().isBlank()) {
                return usuario.getCorreo();
            }
            return usuario.getId();
        }
        return fallback;
    }

    private String hashSha256(byte[] contenido) {
        if (contenido == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(contenido));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizarAccion(String accion, String origen) {
        String value = accion == null ? "" : accion.trim().toUpperCase(Locale.ROOT);
        if ("GUARDADO".equals(value) && "ONLYOFFICE_CALLBACK".equalsIgnoreCase(origen)) {
            return "GUARDADO_ONLYOFFICE";
        }
        return value.isBlank() ? "GUARDADO" : value;
    }
}
