package com.leo.politicas_de_negocio.simulation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeSimulationStats {

    private String nodeId;
    private String nodeName;
    private String nodeType;
    private long executions;
    private double totalEstimatedTimeHours;
    private double averageEstimatedTimeHours;
    private double loadPercentage;
    private boolean bottleneck;
}
