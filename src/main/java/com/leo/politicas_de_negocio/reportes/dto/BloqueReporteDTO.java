package com.leo.politicas_de_negocio.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BloqueReporteDTO {
    private String id;
    private String tipo; // bar, pie, doughnut, line, area, table, matrix, kpi, error
    private String titulo;
    private int orden;
    private int posicion; // alias for orden
    private ResultadoBloqueReporteDTO datos;
    private ResultadoBloqueReporteDTO dataset; // alias for datos
    private ConfiguracionGraficoDTO configuracion;
    private String mensajeError;

    // Helper constructor for errors
    public static BloqueReporteDTO createErrorBlock(String id, String titulo, String errorMsg, int orden) {
        return BloqueReporteDTO.builder()
                .id(id)
                .tipo("error")
                .titulo(titulo)
                .orden(orden)
                .posicion(orden)
                .mensajeError(errorMsg)
                .build();
    }
}
