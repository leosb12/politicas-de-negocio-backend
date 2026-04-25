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
public class ItemColaTareaGuiaFuncionario {

    @JsonProperty("taskId")
    private String idTarea;

    @JsonProperty("taskName")
    private String nombreTarea;

    @JsonProperty("taskStatus")
    private String estadoTarea;

    @JsonProperty("priority")
    private String prioridad;

    @JsonProperty("ageHours")
    private Integer horasAntiguedad;

    @JsonProperty("overdue")
    private boolean atrasada;

    @JsonProperty("policyName")
    private String nombrePolitica;
}
