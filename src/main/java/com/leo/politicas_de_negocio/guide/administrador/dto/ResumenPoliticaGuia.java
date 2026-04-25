package com.leo.politicas_de_negocio.guide.administrador.dto;

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
public class ResumenPoliticaGuia {

    @JsonProperty("hasStartNode")
    private boolean tieneNodoInicio;

    @JsonProperty("hasEndNode")
    private boolean tieneNodoFinal;

    @JsonProperty("totalActivities")
    private int totalActividades;

    @JsonProperty("totalDecisions")
    private int totalDecisiones;

    @JsonProperty("activitiesWithoutResponsible")
    private int actividadesSinResponsable;

    @JsonProperty("activitiesWithoutForm")
    private int actividadesSinFormulario;

    @JsonProperty("invalidConnections")
    private int conexionesInvalidas;

    @JsonProperty("decisionsWithoutRoutes")
    private int decisionesSinRutas;

    @JsonProperty("parallelNodesIncomplete")
    private int nodosParalelosIncompletos;

    @JsonProperty("orphanNodes")
    private int nodosHuerfanos;
}
