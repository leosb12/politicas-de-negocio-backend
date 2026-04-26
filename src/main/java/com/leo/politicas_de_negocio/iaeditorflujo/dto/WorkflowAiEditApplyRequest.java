package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowAiEditApplyRequest {
    private String prompt;
    private List<WorkflowAiEditOperationDto> operations;
}
