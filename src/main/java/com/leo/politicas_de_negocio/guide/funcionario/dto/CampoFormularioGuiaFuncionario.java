package com.leo.politicas_de_negocio.guide.funcionario.dto;

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
public class CampoFormularioGuiaFuncionario {

    @JsonProperty("name")
    private String nombre;

    @JsonProperty("label")
    private String etiqueta;

    @JsonProperty("type")
    private String tipo;

    @JsonProperty("required")
    private boolean obligatorio;

    @JsonProperty("value")
    private Object valor;
}
