package com.leo.politicas_de_negocio.iaeditorflujo.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leo.politicas_de_negocio.analiticas.config.AiServiceUrlBuilder;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditIaRequest;
import com.leo.politicas_de_negocio.iaeditorflujo.dto.WorkflowAiEditProposalResponse;
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

@Component
@RequiredArgsConstructor
public class WorkflowAiEditorClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAiEditorClient.class);
    private static final ObjectMapper JSON = JsonMapper.builder()
            .findAndAddModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private final RestTemplate analyticsIaRestTemplate;
    private final AiServiceUrlBuilder aiServiceUrlBuilder;

    public WorkflowAiEditProposalResponse previewEdition(WorkflowAiEditIaRequest payload) {
        String url = aiServiceUrlBuilder.buildUrl("/api/ia/editar-flujo");
        String serializedPayload = safeJson(payload);
        log.info("[WF-EDIT-IA-REQ] POST {} body={}", url, truncate(serializedPayload));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(serializedPayload, headers);

        try {
            ResponseEntity<String> responseEntity = analyticsIaRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            String body = responseEntity.getBody();
            log.info("[WF-EDIT-IA-RES] {} status={} body={}", url, responseEntity.getStatusCode().value(), truncate(body));
            if (body == null || body.isBlank()) {
                return null;
            }
            return JSON.readValue(body, WorkflowAiEditProposalResponse.class);
        } catch (RestClientResponseException ex) {
            log.error(
                    "[WF-EDIT-IA-ERR] POST {} status={} requestBody={} responseBody={}",
                    url,
                    ex.getStatusCode().value(),
                    truncate(serializedPayload),
                    truncate(ex.getResponseBodyAsString()),
                    ex
            );
            return null;
        } catch (RestClientException ex) {
            log.error(
                    "[WF-EDIT-IA-ERR] POST {} requestBody={} message={}",
                    url,
                    truncate(serializedPayload),
                    ex.getMessage(),
                    ex
            );
            return null;
        } catch (JsonProcessingException ex) {
            log.error("[WF-EDIT-IA-ERR] POST {} response parse error message={}", url, ex.getMessage(), ex);
            return null;
        }
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
