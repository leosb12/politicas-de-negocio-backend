package com.leo.politicas_de_negocio.analiticas.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class PolicyImprovementAnalyticsResponse {

    private String summary;
    private List<Map<String, Object>> policyIssues;
    private String source;
    private boolean available;
}
