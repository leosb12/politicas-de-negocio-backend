package com.leo.politicas_de_negocio.analiticas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttentionTimesAnalyticsResponse {

    private List<PolicyAverageResponse> averageByPolicy;
    private List<NodeAverageResponse> averageByNode;
    private List<DepartmentAverageResponse> averageByDepartment;
    private List<OfficialAverageResponse> averageByOfficial;
    private ActivitySpeedResponse slowestActivity;
    private ActivitySpeedResponse fastestActivity;
    private boolean hasEnoughData;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PolicyAverageResponse {
        private String policyId;
        private String policyName;
        private Double averageHours;
        private long completedInstances;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeAverageResponse {
        private String nodeId;
        private String nodeName;
        private Double averageHours;
        private long completedTasks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DepartmentAverageResponse {
        private String departmentId;
        private String departmentName;
        private Double averageHours;
        private long completedTasks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OfficialAverageResponse {
        private String officialId;
        private String officialName;
        private Double averageHours;
        private long completedTasks;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActivitySpeedResponse {
        private String nodeId;
        private String nodeName;
        private Double averageHours;
    }
}
