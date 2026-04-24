package com.leo.politicas_de_negocio.simulation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "simulation_runs")
@CompoundIndex(name = "idx_simulation_policy_created", def = "{'policyId': 1, 'createdAt': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationRun {

    @Id
    private String id;

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
