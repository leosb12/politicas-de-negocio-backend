package com.leo.politicas_de_negocio.documents.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class S3UploadResult {
    private String bucket;
    private String s3Key;
    private String s3Uri;
    private String s3Url;
    private String eTag;
    private String nombreArchivoSanitizado;
}
