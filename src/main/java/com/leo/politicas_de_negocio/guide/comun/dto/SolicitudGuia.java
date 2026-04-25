package com.leo.politicas_de_negocio.guide.comun.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SolicitudGuia {

    @JsonProperty("userId")
    private String usuarioId;

    @JsonProperty("userName")
    private String nombreUsuario;

    @JsonProperty("role")
    private String rol;

    @JsonProperty("screen")
    private String pantalla;

    @JsonProperty("question")
    private String pregunta;
}
