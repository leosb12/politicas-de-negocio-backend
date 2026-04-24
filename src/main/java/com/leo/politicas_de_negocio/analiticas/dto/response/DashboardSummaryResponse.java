package com.leo.politicas_de_negocio.analiticas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardSummaryResponse {

    private GeneralAnalyticsResponse general;
    private AttentionTimesAnalyticsResponse attentionTimes;
    private TaskAccumulationAnalyticsResponse taskAccumulation;
}
