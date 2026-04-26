package com.leo.politicas_de_negocio.guide.usuario_movil.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumenProgresoGuiaUsuarioMovil {

    @JsonProperty("pasosCompletados")
    private int pasosCompletados;

    @JsonProperty("pasoActual")
    private String pasoActual;

    @JsonProperty("pasosPendientes")
    private int pasosPendientes;

    @JsonProperty("porcentajeAvance")
    private int porcentajeAvance;
}
