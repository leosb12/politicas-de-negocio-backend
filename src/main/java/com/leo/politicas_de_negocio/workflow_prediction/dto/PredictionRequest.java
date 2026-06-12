package com.leo.politicas_de_negocio.workflow_prediction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionRequest {
    private String politicaId;
    private String nombrePolitica;
    private int cantidadObservaciones;
    private int cantidadNodos;
    private int cantidadDecisiones;
    private int cantidadForks;
    private int cantidadJoins;
    private int cantidadRetornos;
    private int cantidadReprocesos;
    private int cantidadDocumentos;
    private int cantidadFuncionariosInvolucrados;
    private double duracionPromedioHistorica;
    private String prioridadActual;
    private String rutaEjecutadaCodificada;
    private String rutaEjecutadaLegible;
    private String carrilesVisitados;
    private String actividadesVisitadas;
    private String politicaEstructuraJson;
    private boolean skipDeepSeek;
}
