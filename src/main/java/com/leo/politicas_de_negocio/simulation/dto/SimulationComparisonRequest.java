package com.leo.politicas_de_negocio.simulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationComparisonRequest {

    private String firstPolicyId;
    private String secondPolicyId;
    private Integer instances;
    private Double baseNodeDurationHours;
    private Double variabilityPercent;
    private Boolean includeAiAnalysis;
    private Long randomSeed;
}
