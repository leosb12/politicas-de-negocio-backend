package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkflowAiEditIaRequest {
    private WorkflowAiEditWorkflowDto workflow;
    private String prompt;
}
