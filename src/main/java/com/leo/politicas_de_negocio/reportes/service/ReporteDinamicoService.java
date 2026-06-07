package com.leo.politicas_de_negocio.reportes.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.politicas_de_negocio.reportes.dto.PreviewResponseDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteRequestDto;
import com.leo.politicas_de_negocio.reportes.dto.ReporteResponseDto;
import com.leo.politicas_de_negocio.reportes.model.ReporteGenerado;
import com.leo.politicas_de_negocio.reportes.repository.ReporteGeneradoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteDinamicoService {

    private final ReporteMongoAggregationBuilder aggregationBuilder;
    private final ReporteGeneradoRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ia.service.url:http://localhost:8010}")
    private String iaServiceUrl;

    public ReporteResponseDto interpretar(ReporteRequestDto request, String usuarioId, String rol) {
        try {
            String url = iaServiceUrl + "/api/ia/reportes/interpretar";
            
            Map<String, String> body = new HashMap<>();
            body.put("texto", request.getTexto());
            body.put("usuarioId", usuarioId);
            body.put("rol", rol);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ReporteResponseDto interpretacion = restTemplate.postForObject(url, entity, ReporteResponseDto.class);
            return interpretacion;
        } catch (Exception e) {
            log.error("Error al comunicarse con IA service: ", e);
            throw new RuntimeException("Error al interpretar reporte con IA.");
        }
    }

    public PreviewResponseDto generarPreview(ReporteResponseDto definicion, String originalText, String usuarioId) {
        if (definicion.getRequiereAclaracion() != null && definicion.getRequiereAclaracion()) {
            throw new IllegalArgumentException("El reporte requiere aclaración, no se puede generar vista previa.");
        }
        
        List<Map> resultados = aggregationBuilder.ejecutarConsulta(definicion);
        
        // Registrar en historial
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(originalText)
                    .jsonInterpretado(objectMapper.writeValueAsString(definicion))
                    .entidadPrincipal(definicion.getEntidadPrincipal())
                    .intencionDetectada(definicion.getIntencionDetectada())
                    .formatoSalida("pantalla")
                    .visualizacion(definicion.getVisualizacion())
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXITO")
                    .cantidadResultados(resultados.size())
                    .confianzaModelo(definicion.getConfianza())
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoria de reporte: ", e);
        }

        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        response.setResultados((List)resultados);
        return response;
    }
    
    public List<ReporteGenerado> getHistorial() {
        return repository.findTop20ByOrderByFechaGeneracionDesc();
    }
}
