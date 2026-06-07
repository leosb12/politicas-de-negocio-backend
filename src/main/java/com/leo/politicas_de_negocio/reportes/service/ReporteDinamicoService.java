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

    /**
     * Interpreta una consulta libre enviándola al Motor IA (FastAPI).
     * La IA interpreta y genera un plan; este servicio valida y orquesta.
     */
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
            log.error("Error al comunicarse con el Motor IA: ", e);
            // Retornar respuesta de fallback en vez de lanzar excepción
            ReporteResponseDto fallback = new ReporteResponseDto();
            fallback.setRequiereAclaracion(true);
            fallback.setPreguntaAclaratoria(
                "El servicio de interpretación IA no está disponible en este momento. " +
                "Por favor, verifica que el servicio ia-deep-learning-service esté ejecutándose."
            );
            fallback.setMotor("MOTOR_FALLBACK");
            fallback.setConfianza(0.0);
            return fallback;
        }
    }

    /**
     * Genera vista previa ejecutando la consulta contra MongoDB.
     * Registra el resultado en el historial de auditoría.
     */
    public PreviewResponseDto generarPreview(ReporteResponseDto definicion, String originalText, String usuarioId) {
        if (definicion.getRequiereAclaracion() != null && definicion.getRequiereAclaracion()) {
            throw new IllegalArgumentException("El reporte requiere aclaración, no se puede generar vista previa.");
        }
        
        // Limitar resultados para preview
        if (definicion.getLimite() == null || definicion.getLimite() > 500) {
            definicion.setLimite(500);
        }
        
        List<Map> resultados = aggregationBuilder.ejecutarConsulta(definicion);
        
        // Registrar en historial de auditoría
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
                    .motorUsado(definicion.getMotor() != null ? definicion.getMotor() : "DESCONOCIDO")
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoría de reporte: ", e);
        }

        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        response.setResultados((List) resultados);
        return response;
    }
    
    /**
     * Genera vista previa para exportación con límites más altos.
     */
    public PreviewResponseDto generarPreviewExportacion(ReporteResponseDto definicion, String originalText, String usuarioId) {
        if (definicion.getRequiereAclaracion() != null && definicion.getRequiereAclaracion()) {
            throw new IllegalArgumentException("El reporte requiere aclaración, no se puede exportar.");
        }
        
        // Permitir más resultados para exportación, máximo 5000
        if (definicion.getLimite() == null || definicion.getLimite() > 5000) {
            definicion.setLimite(5000);
        }
        
        List<Map> resultados = aggregationBuilder.ejecutarConsulta(definicion);
        
        // Registrar exportación en auditoría
        try {
            ReporteGenerado reporte = ReporteGenerado.builder()
                    .usuarioAdminId(usuarioId)
                    .textoOriginal(originalText)
                    .jsonInterpretado(objectMapper.writeValueAsString(definicion))
                    .entidadPrincipal(definicion.getEntidadPrincipal())
                    .intencionDetectada(definicion.getIntencionDetectada())
                    .formatoSalida(definicion.getFormatoSalida() != null ? definicion.getFormatoSalida() : "exportacion")
                    .visualizacion(definicion.getVisualizacion())
                    .fechaGeneracion(LocalDateTime.now())
                    .estado("EXPORTADO")
                    .cantidadResultados(resultados.size())
                    .confianzaModelo(definicion.getConfianza())
                    .motorUsado(definicion.getMotor() != null ? definicion.getMotor() : "DESCONOCIDO")
                    .build();
            repository.save(reporte);
        } catch (Exception e) {
            log.warn("Error al guardar auditoría de exportación: ", e);
        }

        PreviewResponseDto response = new PreviewResponseDto();
        response.setInterpretacion(definicion);
        response.setResultados((List) resultados);
        return response;
    }
    
    public List<ReporteGenerado> getHistorial() {
        return repository.findTop20ByOrderByFechaGeneracionDesc();
    }
}
