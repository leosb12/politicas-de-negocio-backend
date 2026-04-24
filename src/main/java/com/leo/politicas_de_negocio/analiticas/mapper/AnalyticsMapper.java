package com.leo.politicas_de_negocio.analiticas.mapper;

import com.leo.politicas_de_negocio.analiticas.dto.response.AttentionTimesAnalyticsResponse;
import com.leo.politicas_de_negocio.analiticas.dto.response.TaskAccumulationAnalyticsResponse;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsMapper {

    public AttentionTimesAnalyticsResponse.PolicyAverageResponse toPolicyAverage(
            String policyId,
            String policyName,
            Double averageHours,
            long completedInstances
    ) {
        return AttentionTimesAnalyticsResponse.PolicyAverageResponse.builder()
                .policyId(policyId)
                .policyName(policyName)
                .averageHours(averageHours)
                .completedInstances(completedInstances)
                .build();
    }

    public AttentionTimesAnalyticsResponse.NodeAverageResponse toNodeAverage(
            String nodeId,
            String nodeName,
            Double averageHours,
            long completedTasks
    ) {
        return AttentionTimesAnalyticsResponse.NodeAverageResponse.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .averageHours(averageHours)
                .completedTasks(completedTasks)
                .build();
    }

    public AttentionTimesAnalyticsResponse.DepartmentAverageResponse toDepartmentAverage(
            String departmentId,
            String departmentName,
            Double averageHours,
            long completedTasks
    ) {
        return AttentionTimesAnalyticsResponse.DepartmentAverageResponse.builder()
                .departmentId(departmentId)
                .departmentName(departmentName)
                .averageHours(averageHours)
                .completedTasks(completedTasks)
                .build();
    }

    public AttentionTimesAnalyticsResponse.OfficialAverageResponse toOfficialAverage(
            String officialId,
            String officialName,
            Double averageHours,
            long completedTasks
    ) {
        return AttentionTimesAnalyticsResponse.OfficialAverageResponse.builder()
                .officialId(officialId)
                .officialName(officialName)
                .averageHours(averageHours)
                .completedTasks(completedTasks)
                .build();
    }

    public AttentionTimesAnalyticsResponse.ActivitySpeedResponse toActivitySpeed(
            String nodeId,
            String nodeName,
            Double averageHours
    ) {
        return AttentionTimesAnalyticsResponse.ActivitySpeedResponse.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .averageHours(averageHours)
                .build();
    }

    public TaskAccumulationAnalyticsResponse.PendingByOfficialResponse toPendingByOfficial(
            String officialId,
            String officialName,
            long pendingTasks,
            Long oldestTaskAgeHours
    ) {
        return TaskAccumulationAnalyticsResponse.PendingByOfficialResponse.builder()
                .officialId(officialId)
                .officialName(officialName)
                .pendingTasks(pendingTasks)
                .oldestTaskAgeHours(oldestTaskAgeHours)
                .build();
    }

    public TaskAccumulationAnalyticsResponse.PendingByDepartmentResponse toPendingByDepartment(
            String departmentId,
            String departmentName,
            long pendingTasks,
            Long oldestTaskAgeHours
    ) {
        return TaskAccumulationAnalyticsResponse.PendingByDepartmentResponse.builder()
                .departmentId(departmentId)
                .departmentName(departmentName)
                .pendingTasks(pendingTasks)
                .oldestTaskAgeHours(oldestTaskAgeHours)
                .build();
    }

    public TaskAccumulationAnalyticsResponse.PendingByPolicyResponse toPendingByPolicy(
            String policyId,
            String policyName,
            long pendingTasks,
            Long oldestTaskAgeHours
    ) {
        return TaskAccumulationAnalyticsResponse.PendingByPolicyResponse.builder()
                .policyId(policyId)
                .policyName(policyName)
                .pendingTasks(pendingTasks)
                .oldestTaskAgeHours(oldestTaskAgeHours)
                .build();
    }

    public TaskAccumulationAnalyticsResponse.PendingByNodeResponse toPendingByNode(
            String nodeId,
            String nodeName,
            long pendingTasks,
            Long oldestTaskAgeHours
    ) {
        return TaskAccumulationAnalyticsResponse.PendingByNodeResponse.builder()
                .nodeId(nodeId)
                .nodeName(nodeName)
                .pendingTasks(pendingTasks)
                .oldestTaskAgeHours(oldestTaskAgeHours)
                .build();
    }
}
