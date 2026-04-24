package com.leo.politicas_de_negocio.analiticas.dto.response;

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
public class TaskAccumulationAnalyticsResponse {

    private List<PendingByOfficialResponse> pendingByOfficial;
    private List<PendingByDepartmentResponse> pendingByDepartment;
    private List<PendingByPolicyResponse> pendingByPolicy;
    private List<PendingByNodeResponse> pendingByNode;
    private List<OldestPendingTaskResponse> oldestPendingTasks;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingByOfficialResponse {
        private String officialId;
        private String officialName;
        private long pendingTasks;
        private Long oldestTaskAgeHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingByDepartmentResponse {
        private String departmentId;
        private String departmentName;
        private long pendingTasks;
        private Long oldestTaskAgeHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingByPolicyResponse {
        private String policyId;
        private String policyName;
        private long pendingTasks;
        private Long oldestTaskAgeHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PendingByNodeResponse {
        private String nodeId;
        private String nodeName;
        private long pendingTasks;
        private Long oldestTaskAgeHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OldestPendingTaskResponse {
        private String taskId;
        private String policyName;
        private String nodeName;
        private String assignedToName;
        private String departmentName;
        private Long ageHours;
        private LocalDateTime createdAt;
    }
}
