package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentoMetadata;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentoMetadataService {

    public static final String CATEGORIA_REQUISITO_INICIAL = "REQUISITO_INICIAL";

    private static final Logger log = LoggerFactory.getLogger(DocumentoMetadataService.class);

    private final DynamoDbTable<DocumentoMetadata> metadataTable;

    public DocumentoMetadataService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.document-repositories-table}") String tableName) {
        this.metadataTable = enhancedClient.table(
                tableName,
                TableSchema.fromBean(DocumentoMetadata.class));
    }

    public void guardarMetadata(DocumentoMetadata metadata) {
        log.info("Guardando metadata en DynamoDB: PK={}, SK={}", metadata.getPk(), metadata.getSk());
        try {
            metadataTable.putItem(metadata);
            log.info("Metadata guardada exitosamente en DynamoDB");
        } catch (Exception e) {
            log.error("Error al guardar metadata en DynamoDB", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo persistir la metadata del documento en DynamoDB: " + e.getMessage());
        }
    }

    public List<DocumentoMetadata> listarRequisitosInicialesPorTramite(String clienteId, String tramiteId) {
        List<DocumentoMetadata> documentos = listarPorTramite(clienteId, tramiteId);
        return documentos.stream()
                .filter(documento -> documento != null
                        && CATEGORIA_REQUISITO_INICIAL.equalsIgnoreCase(documento.getCategoriaDocumento()))
                .toList();
    }

    public List<DocumentoMetadata> listarPorTramite(String clienteId, String tramiteId) {
        if (clienteId == null || clienteId.isBlank() || tramiteId == null || tramiteId.isBlank()) {
            return List.of();
        }

        List<DocumentoMetadata> documentos = new ArrayList<>();
        try {
            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue("CLIENTE#" + clienteId)
                            .sortValue("TRAMITE#" + tramiteId + "#ARCHIVO#")
                            .build()
            );

            SdkIterable<Page<DocumentoMetadata>> pages = metadataTable.query(r -> r.queryConditional(queryConditional));
            for (Page<DocumentoMetadata> page : pages) {
                documentos.addAll(page.items());
            }
            return documentos;
        } catch (Exception e) {
            log.error("Error consultando metadata documental en DynamoDB: clienteId={}, tramiteId={}",
                    clienteId,
                    tramiteId,
                    e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error consultando metadata documental");
        }
    }
}
