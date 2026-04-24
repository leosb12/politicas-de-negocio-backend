package com.leo.politicas_de_negocio.analiticas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneralAnalyticsResponse {

    private long totalPolicies;
    private long activePolicies;
    private long totalInstances;
    private long inProgressInstances;
    private long completedInstances;
    private long rejectedInstances;
    private long pendingTasks;
    private long completedTasks;
    private Double averageResolutionTimeHours;
    private boolean hasEnoughResolutionTimeData;
}
