package com.leo.politicas_de_negocio.movilia.clasificacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClasificarSolicitudMovilResponse {
    private String politicaId;
    private String nombrePolitica;
    private String descripcionPolitica;
    private Double confianza;
    private String origen;
    private String metodoRecomendacion;
    private Boolean requiereMasInformacion;
    private Boolean requiereConfirmacion;
    private String mensaje;
    private List<String> requisitosDetectados;
    private List<String> requisitosCoincidentes;
    private List<String> requisitosFaltantes;
    private List<TopResultadoClasificacionDto> topResultados;
}
