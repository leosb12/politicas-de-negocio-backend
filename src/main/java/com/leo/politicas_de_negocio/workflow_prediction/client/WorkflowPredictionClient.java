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
    private final String baseUrl;
    private final String url;

    public WorkflowPredictionClient(RestTemplate restTemplate, @Value("${app.ia.deep-learning-url:http://localhost:8010}") String iaServiceUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = iaServiceUrl;
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

    public java.util.Map<String, Object> train() {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // 1. Generar dataset sintético local: POST /api/predicciones/dataset/generar-local
            // Body: {"cantidadPorPolitica": 200}
            String generateUrl = baseUrl + "/api/predicciones/dataset/generar-local";
            java.util.Map<String, Object> genBody = new java.util.HashMap<>();
            genBody.put("cantidadPorPolitica", 200);
            HttpEntity<java.util.Map<String, Object>> genEntity = new HttpEntity<>(genBody, headers);
            restTemplate.postForObject(generateUrl, genEntity, java.util.Map.class);

            // 2. Combinar datasets: POST /api/predicciones/dataset/combinar
            String combineUrl = baseUrl + "/api/predicciones/dataset/combinar";
            HttpEntity<String> emptyEntity = new HttpEntity<>("", headers);
            restTemplate.postForObject(combineUrl, emptyEntity, java.util.Map.class);

            // 3. Entrenar modelos: POST /api/predicciones/train
            String trainUrl = baseUrl + "/api/predicciones/train";
            return restTemplate.postForObject(trainUrl, emptyEntity, java.util.Map.class);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error in training client: " + e.getMessage(), e);
        }
    }
}
