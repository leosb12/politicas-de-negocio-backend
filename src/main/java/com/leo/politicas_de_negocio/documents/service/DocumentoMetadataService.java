package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentoMetadata;
import com.leo.politicas_de_negocio.shared.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Service
public class DocumentoMetadataService {

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
}
