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
public class PasoPosibleGuiaFuncionario {

    @JsonProperty("condition")
    private String condicion;

    @JsonProperty("nextNode")
    private String siguienteNodo;

    @JsonProperty("nextDepartment")
    private String siguienteDepartamento;
}
