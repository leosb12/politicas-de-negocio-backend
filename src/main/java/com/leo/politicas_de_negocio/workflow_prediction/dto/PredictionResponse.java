package com.leo.politicas_de_negocio.workflow_prediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResponse {
    private String riesgoDemora;
    private double probabilidadRiesgoDemora;
    private String cuelloBotella;
    private double probabilidadCuelloBotella;
    private String anomalia;
    private double probabilidadAnomalia;
    private String prioridadRecomendada;
    private String rutaRecomendadaLabel;
    private String rutaRecomendadaLegible;
    private double confianzaRuta;
    private String mensaje;
}
