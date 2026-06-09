package com.leo.politicas_de_negocio.reportes.service;

import com.leo.politicas_de_negocio.reportes.dto.ResultadoBloqueReporteDTO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReporteVisualPromptService {

    private final RestTemplate restTemplate;

    @Value("${ia.service.url:http://localhost:8010}")
    private String iaServiceUrl;

    @Data
    public static class PromptBloqueIntent {
        private String tipo;
        private String intencion;
        private String titulo;
        private int orden;
        private String entidadPrincipal;
        private String metrica;
        private int limite = 10;
        private Map<String, Object> filtros;
        private ResultadoBloqueReporteDTO datos;
    }

    @Data
    public static class PromptReporteResponse {
        private String titulo;
        private String descripcion;
        private List<PromptBloqueIntent> bloques = new ArrayList<>();
    }

    public PromptReporteResponse interpretarPromptVisual(String prompt, String usuarioId) {
        return interpretarPromptVisual(prompt, usuarioId, false, new ArrayList<>(), new ArrayList<>());
    }

    public PromptReporteResponse interpretarPromptVisual(
            String prompt, 
            String usuarioId, 
            Boolean iaPlus, 
            List<String> usuariosReales, 
            List<String> politicasReales) {
        try {
            String url = iaServiceUrl + "/api/ia/reportes-visuales/interpretar";
            log.info("Llamando a FastAPI para interpretar prompt visual: '{}' en URL: {}, iaPlus: {}", prompt, url, iaPlus);

            Map<String, Object> request = new HashMap<>();
            request.put("prompt", prompt);
            request.put("usuarioId", usuarioId);
            request.put("iaPlus", iaPlus);
            request.put("usuariosReales", usuariosReales);
            request.put("politicasReales", politicasReales);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            PromptReporteResponse response = restTemplate.postForObject(url, entity, PromptReporteResponse.class);
            if (response == null) {
                throw new RuntimeException("Respuesta vacía del servicio de IA.");
            }
            return response;
        } catch (Exception e) {
            log.error("Error al comunicarse con el Motor de Reportes Visuales IA: ", e);
            // Fallback manual en caso de desconexión del microservicio de IA
            return generarInterpretacionLocalFallback(prompt);
        }
    }

    private PromptReporteResponse generarInterpretacionLocalFallback(String prompt) {
        log.warn("Generando interpretación local de fallback para el prompt visual...");
        PromptReporteResponse fallback = new PromptReporteResponse();
        fallback.setTitulo("Reporte Visual Inteligente (Modo Offline)");
        fallback.setDescripcion("El microservicio de IA no está disponible. Se aplicó análisis básico local.");

        List<PromptBloqueIntent> bloques = new ArrayList<>();
        String query = prompt.toLowerCase();

        int orden = 1;

        // Búsqueda simple de palabras clave para construir bloques
        if (query.contains("funcionario") && (query.contains("activo") || query.contains("finaliza"))) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo(query.contains("tabla") ? "table" : "bar");
            b.setIntencion("funcionarios_mas_activos");
            b.setMetrica("funcionarios_mas_activos");
            b.setTitulo("Funcionarios más activos");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("usuario") || query.contains("cliente") && query.contains("inicia")) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("pie");
            b.setIntencion("clientes_mas_inician_politicas");
            b.setMetrica("clientes_mas_inician_politicas");
            b.setTitulo("Clientes que más inician políticas");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("administrador") || query.contains("admin") && query.contains("crea")) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("table");
            b.setIntencion("administradores_mas_politicas_crearon");
            b.setMetrica("administradores_mas_politicas_crearon");
            b.setTitulo("Administradores que más políticas crearon");
            b.setEntidadPrincipal("politicas_negocio");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("tramites") && (query.contains("mes") || query.contains("mensual"))) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("line");
            b.setIntencion("tramites_por_mes");
            b.setMetrica("tramites_por_mes");
            b.setTitulo("Trámites iniciados por mes");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("politica") && (query.contains("mas usada") || query.contains("mas recurrentes"))) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("bar");
            b.setIntencion("politicas_mas_usadas");
            b.setMetrica("politicas_mas_usadas");
            b.setTitulo("Políticas de negocio más usadas");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("pago") && (query.contains("total") || query.contains("recaudado"))) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("kpi");
            b.setIntencion("total_pagos");
            b.setMetrica("total_pagos");
            b.setTitulo("Total recaudado");
            b.setEntidadPrincipal("pagos");
            b.setOrden(orden++);
            bloques.add(b);
        }

        if (query.contains("tramite") && (query.contains("total") || query.contains("cantidad"))) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("kpi");
            b.setIntencion("total_tramites");
            b.setMetrica("total_tramites");
            b.setTitulo("Total de trámites");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(orden++);
            bloques.add(b);
        }

        // Si no se reconoció nada, añadir trámites por estado por defecto
        if (bloques.isEmpty()) {
            PromptBloqueIntent b = new PromptBloqueIntent();
            b.setTipo("pie");
            b.setIntencion("tramites_por_state");
            b.setMetrica("tramites_por_estado");
            b.setTitulo("Distribución de trámites por estado");
            b.setEntidadPrincipal("instancias_politica");
            b.setOrden(1);
            bloques.add(b);
        }

        fallback.setBloques(bloques);
        return fallback;
    }
}
