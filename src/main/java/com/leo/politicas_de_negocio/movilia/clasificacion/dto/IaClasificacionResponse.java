package com.leo.politicas_de_negocio.movilia.clasificacion.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class IaClasificacionResponse {
    private String politicaId;
    private String nombrePolitica;
    private Double confianza;
    private String origen;
    private String metodoRecomendacion;
    private Boolean requiereMasInformacion;
    private List<String> requisitosDetectados = new ArrayList<>();
    private List<String> requisitosCoincidentes = new ArrayList<>();
    private List<String> requisitosFaltantes = new ArrayList<>();
    private List<TopResultadoClasificacionDto> topResultados = new ArrayList<>();
}
