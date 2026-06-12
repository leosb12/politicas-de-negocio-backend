package com.leo.politicas_de_negocio.analiticas.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ia")
@Data
public class AnalyticsIaProperties {

    private String baseUrl = "http://localhost:8000";
    private long timeoutMs = 30000L;
}
