package com.leo.politicas_de_negocio.simulation.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leo.politicas_de_negocio.analiticas.config.AnalyticsIaProperties;
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
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SimulationIaClient {

    private static final Logger log = LoggerFactory.getLogger(SimulationIaClient.class);
    private static final ObjectMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private final RestTemplate analyticsIaRestTemplate;
    private final AnalyticsIaProperties analyticsIaProperties;

    public SimulationAiInsightResponse analyzeSimulation(Object payload) {
        return post(
                "/api/ia/simulations/analyze",
                payload,
                SimulationAiInsightResponse.class,
                SimulationAiInsightResponse.unavailable()
        );
    }

    public SimulationAiInsightResponse comparePolicies(Object payload) {
        return post(
                "/api/ia/simulations/compare",
                payload,
                SimulationAiInsightResponse.class,
                SimulationAiInsightResponse.unavailable()
        );
    }

    private <T> T post(String path, Object payload, Class<T> responseType, T fallback) {
        String url = buildUrl(path);

        T directResponse = doPost(url, payload, responseType);
        if (directResponse != null) {
            return directResponse;
        }

        Map<String, Object> wrappedPayload = new LinkedHashMap<>();
        wrappedPayload.put("data", payload);
        T wrappedResponse = doPost(url, wrappedPayload, responseType);
        return wrappedResponse != null ? wrappedResponse : fallback;
    }

    private <T> T doPost(String url, Object payload, Class<T> responseType) {
        String serializedPayload = safeJson(payload);
        log.info("[SIM-IA-REQ] POST {} body={}", url, truncate(serializedPayload));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestEntity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> responseEntity = analyticsIaRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            String body = responseEntity.getBody();
            log.info("[SIM-IA-RES] {} status={} body={}", url, responseEntity.getStatusCode().value(), truncate(body));
            if (body == null || body.isBlank()) {
                return null;
            }
            return JSON.readValue(body, responseType);
        } catch (RestClientResponseException ex) {
            log.error(
                    "[SIM-IA-ERR] POST {} status={} requestBody={} responseBody={}",
                    url,
                    ex.getStatusCode().value(),
                    truncate(serializedPayload),
                    truncate(ex.getResponseBodyAsString()),
                    ex
            );
            return null;
        } catch (RestClientException ex) {
            log.error("[SIM-IA-ERR] POST {} requestBody={} message={}", url, truncate(serializedPayload), ex.getMessage(), ex);
            return null;
        } catch (JsonProcessingException ex) {
            log.error("[SIM-IA-ERR] POST {} response parse error message={}", url, ex.getMessage(), ex);
            return null;
        }
    }

    private String buildUrl(String path) {
        String baseUrl = analyticsIaProperties.getBaseUrl() != null
                ? analyticsIaProperties.getBaseUrl().trim()
                : "http://localhost:8001";
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
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
