package com.leo.politicas_de_negocio.simulation.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationAiInsightResponse {

    private String summary;
    private String source;
    private boolean available;

    public static SimulationAiInsightResponse unavailable() {
        return SimulationAiInsightResponse.builder()
                .summary("El analisis inteligente de simulacion no esta disponible en este momento.")
                .source("AI")
                .available(false)
                .build();
    }
}
