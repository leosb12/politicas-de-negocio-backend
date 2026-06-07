package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class AsistenteDatosResponseDto {
    private String respuesta;
    private String resumen;
    private List<Map<String, Object>> datos;
    private List<String> columnas;
    private String visualizacionSugerida;
    private List<String> accionesSugeridas;
    private List<String> fuentesConsultadas;
    private List<String> advertencias;
    private Map<String, Object> plan;
    private String motor;
    private Double confianza;
}
