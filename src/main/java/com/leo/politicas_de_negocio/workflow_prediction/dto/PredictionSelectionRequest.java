package com.leo.politicas_de_negocio.workflow_prediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionSelectionRequest {
    private String politicaId;
    private boolean predictMejorRuta;
    private boolean predictCuellosBotella;
    private boolean predictAnomalias;
    private boolean predictPrioridad;
}
