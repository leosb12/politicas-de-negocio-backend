package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class WorkflowAiEditApplyResponse {
    String policyId;
    String policyName;
    boolean success;
    String message;
    int appliedOperations;
    List<WorkflowAiEditOperationDto> operations;
    PoliticaNegocio workflow;
    List<String> warnings;
    List<String> errors;
    LocalDateTime appliedAt;
}
