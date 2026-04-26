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
public class HistorialGuiaUsuarioMovil {

    @JsonProperty("etapa")
    private String etapa;

    @JsonProperty("estado")
    private String estado;

    @JsonProperty("fecha")
    private String fecha;

    @JsonProperty("detalle")
    private String detalle;

    @JsonProperty("responsable")
    private String responsable;
}
