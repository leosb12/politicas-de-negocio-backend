package com.leo.politicas_de_negocio.workflow_prediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionSelectionResponse {
    private String mejorRutaLabel;
    private String mejorRutaLegible;
    private Double confianzaRuta;
    
    private String cuellosBotella;
    private Double probabilidadCuelloBotella;
    
    private String anomalia;
    private Double probabilidadAnomalia;
    
    private String prioridadTareas;
}
