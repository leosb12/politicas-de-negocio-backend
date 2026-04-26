package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkflowAiEditWorkflowDto {
    private String policyId;
    private String policyName;
    private String description;
    private String status;
    private String policyType;
    private String departamentoInicioId;
    private String laneOrientation;
    private Double laneWidth;
    private Double laneHeight;
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
}
