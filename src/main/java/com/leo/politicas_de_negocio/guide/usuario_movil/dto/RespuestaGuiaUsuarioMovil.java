package com.leo.politicas_de_negocio.guide.usuario_movil.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leo.politicas_de_negocio.guide.comun.dto.AccionGuia;
import com.leo.politicas_de_negocio.guide.comun.dto.RespuestaGuia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RespuestaGuiaUsuarioMovil extends RespuestaGuia {

    @JsonProperty("estadoExplicado")
    private String estadoExplicado;

    @JsonProperty("progresoExplicado")
    private String progresoExplicado;

    @Builder.Default
    @JsonProperty("documentosFaltantes")
    private List<String> documentosFaltantes = new ArrayList<>();

    @Builder.Default
    @JsonProperty("proximosPasos")
    private List<String> proximosPasos = new ArrayList<>();

    @Builder.Default
    @JsonProperty("accionesSugeridas")
    private List<AccionGuia> accionesSugeridas = new ArrayList<>();
}
