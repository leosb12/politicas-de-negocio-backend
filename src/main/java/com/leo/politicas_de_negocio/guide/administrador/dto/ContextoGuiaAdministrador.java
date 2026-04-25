package com.leo.politicas_de_negocio.guide.administrador.dto;

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
public class ContextoGuiaAdministrador {

    @JsonProperty("policyId")
    private String politicaId;

    @JsonProperty("policyName")
    private String nombrePolitica;

    @JsonProperty("policyStatus")
    private String estadoPolitica;

    @JsonProperty("selectedNodeId")
    private String nodoSeleccionadoId;

    @JsonProperty("selectedNode")
    private NodoSeleccionadoGuiaAdministrador nodoSeleccionado;

    @JsonProperty("policySummary")
    private ResumenPoliticaGuia resumenPolitica;

    @Builder.Default
    @JsonProperty("detectedIssues")
    private List<ProblemaDetectadoGuiaAdministrador> problemasDetectados = new ArrayList<>();

    @Builder.Default
    @JsonProperty("availableActions")
    private List<String> accionesDisponibles = new ArrayList<>();

    @Builder.Default
    @JsonProperty("policyDepartments")
    private List<String> departamentosPolitica = new ArrayList<>();
}
