package com.leo.politicas_de_negocio.iaeditorflujo.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class WorkflowAiEditValidationResult {
    boolean valid;
    List<String> warnings;
    List<String> errors;
}
