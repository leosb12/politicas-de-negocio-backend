package com.leo.politicas_de_negocio.archivos.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "s3")
@RequiredArgsConstructor
public class S3StorageConfig {

    private final StorageProperties storageProperties;

    @Bean
    public S3Client s3Client() {
        String region = storageProperties.getS3() != null ? storageProperties.getS3().getRegion() : null;
        if (!StringUtils.hasText(region)) {
            throw new IllegalStateException("Debe configurar app.storage.s3.region para usar almacenamiento S3");
        }

        return S3Client.builder()
                .region(Region.of(region.trim()))
                .build();
    }
}
