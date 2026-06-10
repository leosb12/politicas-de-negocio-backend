package com.leo.politicas_de_negocio.reportes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteVisualRequestDTO {
    private String prompt;
    private String usuarioId;
    
    @JsonProperty("iaPlus")
    private Boolean iaPlus;
}
