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
public class ResumenDashboardGuiaFuncionario {

    @JsonProperty("pendingTasks")
    private int tareasPendientes;

    @JsonProperty("inProgressTasks")
    private int tareasEnProceso;

    @JsonProperty("completedTasks")
    private int tareasCompletadas;

    @JsonProperty("overdueTasks")
    private int tareasAtrasadas;
}
