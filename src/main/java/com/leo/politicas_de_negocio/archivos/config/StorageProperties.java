package com.leo.politicas_de_negocio.archivos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
@Data
public class StorageProperties {

    private String type = "local";
    private Local local = new Local();
    private S3 s3 = new S3();

    @Data
    public static class Local {
        private String basePath = "uploads";
    }

    @Data
    public static class S3 {
        private String bucket;
        private String region;
        private String keyPrefix = "adjuntos/";
    }
}
