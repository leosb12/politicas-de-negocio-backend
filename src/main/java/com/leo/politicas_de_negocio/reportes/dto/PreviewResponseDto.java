package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PreviewResponseDto {
    private ReporteResponseDto interpretacion;
    private List<Map<String, Object>> resultados;
}
