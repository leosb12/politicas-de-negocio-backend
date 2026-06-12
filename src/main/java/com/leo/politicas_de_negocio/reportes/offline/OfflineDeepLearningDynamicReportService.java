package com.leo.politicas_de_negocio.reportes.offline;

import com.leo.politicas_de_negocio.reportes.service.ReporteVisualPromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineDeepLearningDynamicReportService {

    private final RestTemplate restTemplate;

    @Value("${offline.reports.ia.local-url:http://localhost:8010}")
    private String localIaUrl;

    @Value("${offline.reports.ia.local-only:true}")
    private boolean localOnly;

    public ReporteVisualPromptService.PromptReporteResponse interpretarOffline(String prompt, String usuarioId, Boolean iaPlus) {
        String url = localIaUrl + "/api/ia/reportes-visuales/interpretar";
        log.info("OFFLINE_LOCAL_DEEP_LEARNING_REQUEST: Enviando prompt al servicio de IA local: {}", url);

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("usuarioId", usuarioId);
            request.put("iaPlus", iaPlus);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (localOnly) {
                log.info("CLOUD_AI_DISABLED_BY_OFFLINE_MODE: Inyectando headers restrictivos locales");
                headers.set("X-Offline-Mode", "true");
                headers.set("X-Local-Deep-Learning-Only", "true");
                headers.set("X-Disable-Cloud-AI", "true");
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            ReporteVisualPromptService.PromptReporteResponse response = 
                    restTemplate.postForObject(url, entity, ReporteVisualPromptService.PromptReporteResponse.class);

            if (response == null) {
                throw new RuntimeException("Respuesta vacía recibida de FastAPI local.");
            }

            log.info("OFFLINE_LOCAL_DEEP_LEARNING_RESPONSE: Respuesta del modelo local Keras recibida de forma exitosa (Bloques: {})", 
                    response.getBloques() != null ? response.getBloques().size() : 0);
            return response;

        } catch (Exception e) {
            log.error("Error al comunicarse con FastAPI local en url: {}. Falló la inferencia local de IA: {}", url, e.getMessage());
            throw new RuntimeException("El servicio de IA local (FastAPI) no está disponible en " + localIaUrl, e);
        }
    }
}
