package com.leo.politicas_de_negocio.analiticas.client;

import com.leo.politicas_de_negocio.analiticas.config.AiServiceUrlBuilder;
import com.leo.politicas_de_negocio.analiticas.dto.response.BottlenecksAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.PolicyImprovementAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskRedistributionAnalyticsResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyticsIaClient {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsIaClient.class);
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final RestTemplate analyticsIaRestTemplate;
    private final AiServiceUrlBuilder aiServiceUrlBuilder;

    public BottlenecksAnalyticsResponse analyzeBottlenecks(Object dashboard) {
        return post(
                "/api/ia/analytics/bottlenecks",
                dashboard,
                BottlenecksAnalyticsResponse.class,
                BottlenecksAnalyticsResponse.builder()
                        .summary("El analisis inteligente no esta disponible en este momento.")
                        .bottlenecks(List.of())
                        .source("AI")
                        .available(false)
                        .build()
        );
    }

    public TaskRedistributionAnalyticsResponse analyzeTaskRedistribution(Object dashboard) {
        return post(
                "/api/ia/analytics/task-redistribution",
                dashboard,
                TaskRedistributionAnalyticsResponse.class,
                TaskRedistributionAnalyticsResponse.builder()
                        .summary("El analisis inteligente no esta disponible en este momento.")
                        .recommendations(List.of())
                        .source("AI")
                        .available(false)
                        .build()
        );
    }

    public PolicyImprovementAnalyticsResponse analyzePolicyImprovement(Object dashboard) {
        return post(
                "/api/ia/analytics/policy-improvement",
                dashboard,
                PolicyImprovementAnalyticsResponse.class,
                PolicyImprovementAnalyticsResponse.builder()
                        .summary("El analisis inteligente no esta disponible en este momento.")
                        .policyIssues(List.of())
                        .source("AI")
                        .available(false)
                        .build()
        );
    }

    private <T> T post(String path, Object payload, Class<T> responseType, T fallback) {
        String url = aiServiceUrlBuilder.buildUrl(path);

        T primary = doPost(url, payload, responseType);
        if (isValidResponse(primary)) {
            return primary;
        }

        // Compatibilidad con variantes de contrato donde FastAPI espera {"dashboard": ...}
        Map<String, Object> wrappedPayload = new LinkedHashMap<>();
        wrappedPayload.put("dashboard", payload);
        T wrapped = doPost(url, wrappedPayload, responseType);
        if (isValidResponse(wrapped)) {
            return wrapped;
        }

        log.warn("IA devolvio respuesta no util en {}. Se retorna fallback.", url);
        return fallback;
    }

    private <T> T doPost(String url, Object payload, Class<T> responseType) {
        String serializedPayload = safeJson(payload);
        log.info("[IA-REQ] POST {} payloadChars={}", url, serializedPayload.length());
        log.debug("[IA-REQ-BODY] POST {} body={}", url, truncate(serializedPayload));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestEntity = new HttpEntity<>(payload, headers);
        Instant startedAt = Instant.now();

        try {
            ResponseEntity<String> responseEntity = analyticsIaRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            String responseBody = responseEntity.getBody();
            log.info(
                    "[IA-RES] {} status={} durationMs={} responseChars={}",
                    url,
                    responseEntity.getStatusCode().value(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    responseBody != null ? responseBody.length() : 0
            );
            log.debug("[IA-RES-BODY] {} body={}", url, truncate(responseBody));

            if (responseBody == null || responseBody.isBlank()) {
                return null;
            }

            return JSON.readValue(responseBody, responseType);
        } catch (RestClientResponseException ex) {
            log.error(
                    "[IA-ERR] POST {} status={} requestBody={} responseBody={}",
                    url,
                    ex.getStatusCode().value(),
                    truncate(serializedPayload),
                    truncate(ex.getResponseBodyAsString()),
                    ex
            );
            return null;
        } catch (RestClientException ex) {
            log.error(
                    "[IA-ERR] POST {} requestBody={} message={}",
                    url,
                    truncate(serializedPayload),
                    ex.getMessage(),
                    ex
            );
            return null;
        } catch (JsonProcessingException ex) {
            log.error(
                    "[IA-ERR] POST {} no se pudo deserializar responseType={} requestBody={} message={}",
                    url,
                    responseType.getSimpleName(),
                    truncate(serializedPayload),
                    ex.getMessage(),
                    ex
            );
            return null;
        } catch (Exception ex) {
            log.error(
                    "[IA-ERR] Error inesperado en POST {} requestBody={}",
                    url,
                    truncate(serializedPayload),
                    ex
            );
            return null;
        }
    }

    private boolean isValidResponse(Object response) {
        if (response == null) {
            return false;
        }
        if (response instanceof BottlenecksAnalyticsResponse bottlenecks) {
            return bottlenecks.isAvailable();
        }
        if (response instanceof TaskRedistributionAnalyticsResponse taskRedistribution) {
            return taskRedistribution.isAvailable();
        }
        if (response instanceof PolicyImprovementAnalyticsResponse policyImprovement) {
            return policyImprovement.isAvailable();
        }
        return false;
    }

    private String safeJson(Object value) {
        if (value == null) {
            return "null";
        }
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "null";
        }
        int limit = 3000;
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...";
    }

}
