package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowAiEditProposalResponse {
    private boolean success;
    private String intent;
    private String summary;
    private List<WorkflowAiEditOperationDto> operations;
    private List<String> warnings;
    private List<String> errors;
    private boolean requiresConfirmation;
}
