package com.leo.politicas_de_negocio.guide.usuario_movil.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContextoGuiaUsuarioMovil {

    @JsonProperty("tramiteId")
    private String tramiteId;

    @JsonProperty("politicaId")
    private String politicaId;

    @JsonProperty("nombrePolitica")
    private String nombrePolitica;

    @JsonProperty("estadoTramite")
    private String estadoTramite;

    @JsonProperty("etapaActual")
    private ContextoEtapaActualGuiaUsuarioMovil etapaActual;

    @JsonProperty("resumenProgreso")
    private ResumenProgresoGuiaUsuarioMovil resumenProgreso;

    @Builder.Default
    @JsonProperty("historial")
    private List<HistorialGuiaUsuarioMovil> historial = new ArrayList<>();

    @Builder.Default
    @JsonProperty("documentosFaltantes")
    private List<String> documentosFaltantes = new ArrayList<>();

    @Builder.Default
    @JsonProperty("observaciones")
    private List<String> observaciones = new ArrayList<>();

    @Builder.Default
    @JsonProperty("proximosPasos")
    private List<String> proximosPasos = new ArrayList<>();

    @Builder.Default
    @JsonProperty("accionesDisponibles")
    private List<String> accionesDisponibles = new ArrayList<>();
}
