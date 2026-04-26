package com.leo.politicas_de_negocio.analiticas.config;

import org.springframework.stereotype.Component;

@Component
public class AiServiceUrlBuilder {

    private final AnalyticsIaProperties analyticsIaProperties;

    public AiServiceUrlBuilder(AnalyticsIaProperties analyticsIaProperties) {
        this.analyticsIaProperties = analyticsIaProperties;
    }

    public String buildUrl(String path) {
        String baseUrl = analyticsIaProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("La propiedad app.ia.base-url es obligatoria");
        }

        String normalizedBaseUrl = baseUrl.trim();
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }

        String normalizedPath = path != null ? path.trim() : "";
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }

        return normalizedBaseUrl + normalizedPath;
    }
}
