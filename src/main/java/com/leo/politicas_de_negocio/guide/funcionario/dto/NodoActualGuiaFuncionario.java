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
public class NodoActualGuiaFuncionario {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private String tipo;

    @JsonProperty("name")
    private String nombre;

    @JsonProperty("description")
    private String descripcion;

    @JsonProperty("department")
    private String departamento;

    @JsonProperty("estimatedTime")
    private String tiempoEstimado;
}
