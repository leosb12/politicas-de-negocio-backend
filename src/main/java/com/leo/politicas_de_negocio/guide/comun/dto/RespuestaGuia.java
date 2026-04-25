package com.leo.politicas_de_negocio.guide.comun.dto;

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
public class RespuestaGuia {

    @JsonProperty("answer")
    private String respuesta;

    @Builder.Default
    @JsonProperty("steps")
    private List<String> pasos = new ArrayList<>();

    @Builder.Default
    @JsonProperty("severity")
    private String severidad = "INFO";

    @JsonProperty("intent")
    private String intencion;

    @Builder.Default
    @JsonProperty("source")
    private String fuente = "BACKEND_FALLBACK";

    @Builder.Default
    @JsonProperty("available")
    private boolean disponible = true;
}
