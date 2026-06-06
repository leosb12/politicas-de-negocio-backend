package com.leo.politicas_de_negocio.documents.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class DocumentoMetadata {

    private String pk; // mapped to clientId attribute
    private String sk; // mapped to repositoryId attribute

    private String repositoryId;
    private String clienteId;
    private String clienteNombre;
    private String tramiteId;
    private String tramiteNombre;
    private String tramiteCodigo;
    private String archivoId;
    private String campoFormularioId;
    private String categoriaDocumento;
    private String nombreArchivoOriginal;
    private String nombreArchivoSanitizado;
    private String tipoArchivo;
    private String extension;
    private Long tamanoBytes;
    private String s3Bucket;
    private String s3Key;
    private String s3Uri;
    private String s3Url;
    private String subidoPorUsuarioId;
    private String subidoPorNombre;
    private String subidoPorRol;
    private String fechaSubida;
    private String estadoDocumento;
    private Integer version;
    private String reemplazaAArchivoId;
    private String origenCarga;
    private String checksum;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("clientId")
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("repositoryId")
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    @DynamoDbAttribute("docRepositoryId")
    public String getRepositoryId() { return repositoryId; }
    public void setRepositoryId(String repositoryId) { this.repositoryId = repositoryId; }

    public String getClienteId() { return clienteId; }
    public void setClienteId(String clienteId) { this.clienteId = clienteId; }

    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }

    public String getTramiteId() { return tramiteId; }
    public void setTramiteId(String tramiteId) { this.tramiteId = tramiteId; }

    public String getTramiteNombre() { return tramiteNombre; }
    public void setTramiteNombre(String tramiteNombre) { this.tramiteNombre = tramiteNombre; }

    public String getTramiteCodigo() { return tramiteCodigo; }
    public void setTramiteCodigo(String tramiteCodigo) { this.tramiteCodigo = tramiteCodigo; }

    public String getArchivoId() { return archivoId; }
    public void setArchivoId(String archivoId) { this.archivoId = archivoId; }

    public String getCampoFormularioId() { return campoFormularioId; }
    public void setCampoFormularioId(String campoFormularioId) { this.campoFormularioId = campoFormularioId; }

    public String getCategoriaDocumento() { return categoriaDocumento; }
    public void setCategoriaDocumento(String categoriaDocumento) { this.categoriaDocumento = categoriaDocumento; }

    public String getNombreArchivoOriginal() { return nombreArchivoOriginal; }
    public void setNombreArchivoOriginal(String nombreArchivoOriginal) { this.nombreArchivoOriginal = nombreArchivoOriginal; }

    public String getNombreArchivoSanitizado() { return nombreArchivoSanitizado; }
    public void setNombreArchivoSanitizado(String nombreArchivoSanitizado) { this.nombreArchivoSanitizado = nombreArchivoSanitizado; }

    public String getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(String tipoArchivo) { this.tipoArchivo = tipoArchivo; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }

    public String getS3Bucket() { return s3Bucket; }
    public void setS3Bucket(String s3Bucket) { this.s3Bucket = s3Bucket; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String s3Key) { this.s3Key = s3Key; }

    public String getS3Uri() { return s3Uri; }
    public void setS3Uri(String s3Uri) { this.s3Uri = s3Uri; }

    public String getS3Url() { return s3Url; }
    public void setS3Url(String s3Url) { this.s3Url = s3Url; }

    public String getSubidoPorUsuarioId() { return subidoPorUsuarioId; }
    public void setSubidoPorUsuarioId(String subidoPorUsuarioId) { this.subidoPorUsuarioId = subidoPorUsuarioId; }

    public String getSubidoPorNombre() { return subidoPorNombre; }
    public void setSubidoPorNombre(String subidoPorNombre) { this.subidoPorNombre = subidoPorNombre; }

    public String getSubidoPorRol() { return subidoPorRol; }
    public void setSubidoPorRol(String subidoPorRol) { this.subidoPorRol = subidoPorRol; }

    public String getFechaSubida() { return fechaSubida; }
    public void setFechaSubida(String fechaSubida) { this.fechaSubida = fechaSubida; }

    public String getEstadoDocumento() { return estadoDocumento; }
    public void setEstadoDocumento(String estadoDocumento) { this.estadoDocumento = estadoDocumento; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getReemplazaAArchivoId() { return reemplazaAArchivoId; }
    public void setReemplazaAArchivoId(String reemplazaAArchivoId) { this.reemplazaAArchivoId = reemplazaAArchivoId; }

    public String getOrigenCarga() { return origenCarga; }
    public void setOrigenCarga(String origenCarga) { this.origenCarga = origenCarga; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
