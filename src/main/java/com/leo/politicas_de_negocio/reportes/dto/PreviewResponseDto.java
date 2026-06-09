package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class PreviewResponseDto {
    private ReporteResponseDto interpretacion;
    private List<Map<String, Object>> filas;
    private List<String> columnas;
    private long total;
    private String mensaje;
    private String detalleTecnico;
    private String error;
    private List<String> sugerencias;
    private Object pipeline;
    private DiagnosticoConsulta diagnostico;
    private Map<String, Object> reporteCompuesto;
    private Boolean asistido;

    @Data
    public static class DiagnosticoConsulta {
        private boolean coleccionTieneDatos;
        private boolean campoExiste;
        private String valorSolicitado;
        private List<String> valoresDisponibles;
    }
}
