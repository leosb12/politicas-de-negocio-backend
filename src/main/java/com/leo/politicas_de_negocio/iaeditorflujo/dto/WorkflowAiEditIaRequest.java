package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class WorkflowAiEditIaRequest {
    private WorkflowAiEditWorkflowDto workflow;
    private String prompt;
    private Map<String, Object> context;
}
