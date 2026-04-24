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
public class PolicyComparisonResult {

    private String firstPolicyId;
    private String firstPolicyName;
    private String secondPolicyId;
    private String secondPolicyName;
    private double firstAverageEstimatedTimeHours;
    private double secondAverageEstimatedTimeHours;
    private long firstBottleneckCount;
    private long secondBottleneckCount;
    private double averageTimeDifferenceHours;
    private String moreEfficientPolicyId;
    private String moreEfficientPolicyName;
    private String conclusion;
    private List<String> comparisonHighlights;
    private SimulationResult firstPolicyResult;
    private SimulationResult secondPolicyResult;
    private String aiSummary;
    private String aiSource;
    private boolean aiAvailable;
    private LocalDateTime comparedAt;
}
