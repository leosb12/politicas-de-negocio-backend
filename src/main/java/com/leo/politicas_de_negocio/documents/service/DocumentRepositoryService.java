package com.leo.politicas_de_negocio.documents.service;

import com.leo.politicas_de_negocio.documents.model.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

@Service
public class DocumentRepositoryService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRepositoryService.class);
    private final DynamoDbTable<DocumentRepository> repositoryTable;

    public DocumentRepositoryService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.document-repositories-table}") String tableName) {
        this.repositoryTable = enhancedClient.table(
                tableName,
                TableSchema.fromBean(DocumentRepository.class));
    }

    public DocumentRepository createAutomatically(
            String clientId,
            String processInstanceId,
            String policyId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }

        if (processInstanceId == null || processInstanceId.isBlank()) {
            throw new IllegalArgumentException("processInstanceId is required");
        }

        String now = Instant.now().toString();

        DocumentRepository repository = new DocumentRepository();
        repository.setClientId(clientId);
        repository.setRepositoryId("repo_" + UUID.randomUUID());
        repository.setProcessInstanceId(processInstanceId);
        repository.setPolicyId(policyId);
        repository.setStatus("ACTIVE");
        repository.setS3Prefix("document-repositories/" + clientId + "/" + processInstanceId + "/");
        repository.setCreatedBy("SYSTEM");
        repository.setCreatedAt(now);
        repository.setUpdatedAt(now);

        repositoryTable.putItem(repository);

        return repository;
    }

    public String obtenerORegistrarRepositoryId(String clientId, String processInstanceId, String policyId) {
        log.info("Buscando repositorio existente para clientId: {}", clientId);
        try {
            QueryConditional queryConditional = QueryConditional.keyEqualTo(
                    Key.builder().partitionValue(clientId).build()
            );
            
            SdkIterable<Page<DocumentRepository>> pages = repositoryTable.query(r -> r.queryConditional(queryConditional));

            for (Page<DocumentRepository> page : pages) {
                for (DocumentRepository repo : page.items()) {
                    if (repo.getRepositoryId() != null && "ACTIVE".equalsIgnoreCase(repo.getStatus())) {
                        log.info("Repositorio existente encontrado: {}", repo.getRepositoryId());
                        return repo.getRepositoryId();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error al buscar repositorio en DynamoDB, se creará uno nuevo: {}", e.getMessage());
        }

        log.info("No se encontró repositorio activo, creando uno nuevo...");
        DocumentRepository newRepo = createAutomatically(clientId, processInstanceId, policyId);
        return newRepo.getRepositoryId();
    }
}
