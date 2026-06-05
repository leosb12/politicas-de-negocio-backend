package com.leo.politicas_de_negocio.documents.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamoDbBean
public class DocumentoVersion {
    private String clientId;
    private String repositoryId;

    private String clienteId;
    private String documentoId;
    private String archivoId;
    private String tipoRegistro;
    private Integer numeroVersion;
    private String s3KeyVersion;
    private String s3KeyActual;
    private String nombreArchivo;
    private String createdAt;
    private String createdBy;
    private String creadoPorUsuarioId;
    private String creadoPorNombre;
    private String fechaCreacion;
    private String origen;
    private String accion;
    private Long tamanioBytes;
    private String checksum;
    private String hashArchivoOpcional;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("clientId")
    public String getClientId() {
        return clientId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("repositoryId")
    public String getRepositoryId() {
        return repositoryId;
    }
}
