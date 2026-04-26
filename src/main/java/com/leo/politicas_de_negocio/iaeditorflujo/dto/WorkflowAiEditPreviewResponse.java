package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class WorkflowAiEditPreviewResponse {
    String policyId;
    String policyName;
    boolean success;
    boolean valid;
    String intent;
    String summary;
    List<WorkflowAiEditOperationDto> operations;
    List<String> warnings;
    List<String> errors;
    boolean requiresConfirmation;
    LocalDateTime generatedAt;
}
