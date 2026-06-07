package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;
import java.util.List;

@Data
public class ReporteResponseDto {
    private String titulo;
    private String descripcion;
    private String intencionDetectada;
    private String entidadPrincipal;
    private List<String> campos;
    private List<MetricaDto> metricas;
    private List<FiltroDto> filtros;
    private List<String> agrupaciones;
    private List<OrdenamientoDto> ordenamiento;
    private Integer limite;
    private String formatoSalida;
    private String visualizacion;
    private Boolean requiereAclaracion;
    private String preguntaAclaratoria;
    private List<String> opcionesSugeridas;
    private Double confianza;
    private String motor;
    private String respuestaNatural;
}
