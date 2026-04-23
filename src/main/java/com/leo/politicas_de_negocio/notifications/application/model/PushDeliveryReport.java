package com.leo.politicas_de_negocio.notifications.application.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PushDeliveryReport {

    private int totalTokens;
    private int successCount;
    private int failureCount;
    private int deactivatedTokenCount;
    private List<PushDeliveryResult> results;

    public static PushDeliveryReport empty() {
        return PushDeliveryReport.builder()
                .totalTokens(0)
                .successCount(0)
                .failureCount(0)
                .deactivatedTokenCount(0)
                .results(List.of())
                .build();
    }

    public static PushDeliveryReport from(List<PushDeliveryResult> results, int deactivatedTokenCount) {
        long successCount = results.stream()
                .filter(PushDeliveryResult::isSuccess)
                .count();

        return PushDeliveryReport.builder()
                .totalTokens(results.size())
                .successCount(Math.toIntExact(successCount))
                .failureCount(Math.toIntExact(results.size() - successCount))
                .deactivatedTokenCount(deactivatedTokenCount)
                .results(List.copyOf(results))
                .build();
    }
}
