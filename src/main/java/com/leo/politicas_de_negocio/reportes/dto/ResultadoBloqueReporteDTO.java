package com.leo.politicas_de_negocio.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultadoBloqueReporteDTO {
    private List<String> labels;
    private List<Number> values;
    private List<String> columns;
    private List<List<Object>> rows;
}
