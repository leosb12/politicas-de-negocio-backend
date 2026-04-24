package com.leo.politicas_de_negocio.simulation.dto;

import com.leo.politicas_de_negocio.simulation.model.SimulationResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRunResponse {

    private String simulationId;
    private String policyId;
    private String policyName;
    private long instances;
    private double baseNodeDurationHours;
    private double variabilityPercent;
    private boolean includeAiAnalysis;
    private Long randomSeed;
    private String createdBy;
    private LocalDateTime createdAt;
    private SimulationResult result;
}
