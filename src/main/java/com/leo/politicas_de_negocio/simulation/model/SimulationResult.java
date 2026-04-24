package com.leo.politicas_de_negocio.simulation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationResult {

    private long instancesSimulated;
    private double totalEstimatedTimeHours;
    private double averageEstimatedTimeHours;
    private String highestLoadNodeId;
    private String highestLoadNodeName;
    private double highestLoadPercentage;
    private List<String> bottleneckNodeIds;
    private List<String> bottleneckNodeNames;
    private List<NodeSimulationStats> nodeStats;
    private List<DecisionSimulationStats> decisionStats;
    private List<String> warnings;
    private String aiSummary;
    private String aiSource;
    private boolean aiAvailable;
    private LocalDateTime generatedAt;
}
