package com.leo.politicas_de_negocio.guide.funcionario.dto;

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
public class ContextoGuiaFuncionario {

    @JsonProperty("taskId")
    private String tareaId;

    @JsonProperty("instanceId")
    private String instanciaId;

    @JsonProperty("policyId")
    private String politicaId;

    @JsonProperty("policyName")
    private String nombrePolitica;

    @JsonProperty("currentNode")
    private NodoActualGuiaFuncionario nodoActual;

    @JsonProperty("taskStatus")
    private String estadoTarea;

    @JsonProperty("priority")
    private String prioridad;

    @JsonProperty("form")
    private FormularioGuiaFuncionario formulario;

    @JsonProperty("historySummary")
    private ResumenHistorialGuiaFuncionario resumenHistorial;

    @Builder.Default
    @JsonProperty("nextPossibleSteps")
    private List<PasoPosibleGuiaFuncionario> pasosPosibles = new ArrayList<>();

    @JsonProperty("dashboardSummary")
    private ResumenDashboardGuiaFuncionario resumenDashboard;

    @Builder.Default
    @JsonProperty("taskQueue")
    private List<ItemColaTareaGuiaFuncionario> colaTareas = new ArrayList<>();

    @Builder.Default
    @JsonProperty("availableActions")
    private List<String> accionesDisponibles = new ArrayList<>();
}
