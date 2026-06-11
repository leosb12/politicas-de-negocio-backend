package com.leo.politicas_de_negocio.workflow_prediction.controller;

import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.repository.PoliticaNegocioRepository;
import com.leo.politicas_de_negocio.workflow_prediction.client.WorkflowPredictionClient;
import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionRequest;
import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionResponse;
import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionSelectionRequest;
import com.leo.politicas_de_negocio.workflow_prediction.dto.PredictionSelectionResponse;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predicciones")
public class WorkflowPredictionController {

    private final WorkflowPredictionClient predictionClient;
    private final PoliticaNegocioRepository politicaRepository;

    public WorkflowPredictionController(WorkflowPredictionClient predictionClient, PoliticaNegocioRepository politicaRepository) {
        this.predictionClient = predictionClient;
        this.politicaRepository = politicaRepository;
    }

    @PostMapping("/policy-analysis")
    public ResponseEntity<java.util.Map<String, Object>> getPolicyPredictions(@RequestBody PredictionSelectionRequest request) {
        PoliticaNegocio politica = politicaRepository.findById(request.getPoliticaId()).orElse(null);
        if (politica == null) {
            return ResponseEntity.badRequest().build();
        }

        String politicaJson = "{}";
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            java.util.Map<String, Object> safeMap = new java.util.HashMap<>();
            safeMap.put("nodos", politica.getNodos());
            safeMap.put("conexiones", politica.getConexiones());
            politicaJson = mapper.writeValueAsString(safeMap);
        } catch (Exception e) {
            e.printStackTrace();
        }

        PredictionRequest clientReq = PredictionRequest.builder()
                .politicaId(politica.getId())
                .nombrePolitica(politica.getNombre())
                .cantidadNodos(politica.getNodos() != null ? politica.getNodos().size() : 0)
                .cantidadDecisiones(politica.getNodos() != null ? (int) politica.getNodos().stream().filter(n -> "DECISION".equals(n.getTipo())).count() : 0)
                .cantidadForks(politica.getNodos() != null ? (int) politica.getNodos().stream().filter(n -> "FORK".equals(n.getTipo())).count() : 0)
                .cantidadJoins(politica.getNodos() != null ? (int) politica.getNodos().stream().filter(n -> "JOIN".equals(n.getTipo())).count() : 0)
                .cantidadDocumentos(politica.getNodos() != null ? politica.getNodos().stream().mapToInt(n -> n.getFormulario() != null ? (int) n.getFormulario().stream().filter(f -> "ARCHIVO".equals(f.getTipo()) || "DOCUMENTO_COLABORATIVO".equals(f.getTipo())).count() : 0).sum() : 0)
                .cantidadFuncionariosInvolucrados(politica.getNodos() != null ? (int) politica.getNodos().stream().filter(n -> n.getResponsableId() != null).map(n -> n.getResponsableId()).distinct().count() : 0)
                .duracionPromedioHistorica(24.5) // Default placeholder
                .prioridadActual("NORMAL")
                .rutaEjecutadaCodificada("")
                .rutaEjecutadaLegible("")
                .carrilesVisitados("")
                .actividadesVisitadas("")
                .politicaEstructuraJson(politicaJson)
                .build();

        java.util.Map<String, Object> response;
        try {
            response = predictionClient.predict(clientReq);
        } catch (Exception e) {
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("error", "Error calling predict: " + e.getMessage());
            return ResponseEntity.status(500).body(err);
        }

        if (response == null) {
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("error", "Prediction client returned null");
            return ResponseEntity.status(500).body(err);
        }

        // Return the dynamic rich JSON response from FastAPI directly
        return ResponseEntity.ok(response);
    }

    @PostMapping("/train")
    public ResponseEntity<java.util.Map<String, Object>> trainModels() {
        try {
            java.util.Map<String, Object> result = predictionClient.train();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            java.util.Map<String, Object> err = new java.util.HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }
}
