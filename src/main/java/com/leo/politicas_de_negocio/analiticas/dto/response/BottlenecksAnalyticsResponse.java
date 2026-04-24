package com.leo.politicas_de_negocio.analiticas.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class BottlenecksAnalyticsResponse {

    private String summary;
    private List<BottleneckItem> bottlenecks;
    private String source;
    private boolean available;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BottleneckItem {
        private String type;
        private String name;
        private String severity;
        private String evidence;
        private String impact;
        private String recommendation;
    }
}
