package com.leo.politicas_de_negocio.reportes.dto;

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
public class ReporteVisualDTO {
    private String titulo;
    private String descripcion;
    private String promptOriginal;
    private LocalDateTime fechaGeneracion;
    private List<BloqueReporteDTO> bloques;
    private Boolean asistido;
    private String offlineMessage;
}
