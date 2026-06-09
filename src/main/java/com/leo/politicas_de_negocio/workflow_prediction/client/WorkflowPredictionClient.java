package com.leo.politicas_de_negocio.workflow_prediction.client;

import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionRequest;
import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class WorkflowPredictionClient {

    private final RestTemplate restTemplate;
    private final String url;

    public WorkflowPredictionClient(RestTemplate restTemplate, @Value("${ia.service.url:http://localhost:8010}") String iaServiceUrl) {
        this.restTemplate = restTemplate;
        this.url = iaServiceUrl + "/api/predicciones/predict";
    }

    public java.util.Map<String, Object> predict(PredictionRequest request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<PredictionRequest> entity = new HttpEntity<>(request, headers);
            return restTemplate.postForObject(url, entity, java.util.Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error in prediction client: " + e.getMessage(), e);
        }
    }
}
