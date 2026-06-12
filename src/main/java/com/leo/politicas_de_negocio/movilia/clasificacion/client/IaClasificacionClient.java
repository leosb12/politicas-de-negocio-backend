package com.leo.politicas_de_negocio.movilia.clasificacion.client;

import com.leo.politicas_de_negocio.analiticas.config.AiServiceUrlBuilder;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.IaClasificacionRequest;
import com.leo.politicas_de_negocio.movilia.clasificacion.dto.IaClasificacionResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class IaClasificacionClient {

    private static final Logger log = LoggerFactory.getLogger(IaClasificacionClient.class);

    private final RestTemplate analyticsIaRestTemplate;
    private final AiServiceUrlBuilder aiServiceUrlBuilder;

    public IaClasificacionResponse clasificar(IaClasificacionRequest payload, String aiMode) {
        String url = aiServiceUrlBuilder.buildUrl("/api/ia/clasificar-solicitud");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (aiMode != null && !aiMode.trim().isEmpty()) {
            headers.set("X-AI-Mode", aiMode.trim());
        }
        HttpEntity<IaClasificacionRequest> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<IaClasificacionResponse> response = analyticsIaRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    IaClasificacionResponse.class
            );
            return response.getBody();
        } catch (RestClientException ex) {
            log.error("[CU35-IA-ERR] No se pudo clasificar solicitud con ia-service: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
