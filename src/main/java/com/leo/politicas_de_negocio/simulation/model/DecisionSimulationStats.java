package com.leo.politicas_de_negocio.simulation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DecisionSimulationStats {

    private String nodeId;
    private String nodeName;
    private long totalDecisions;
    private Map<String, Long> outcomes;
}
