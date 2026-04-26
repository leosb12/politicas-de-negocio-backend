package com.leo.politicas_de_negocio.formulariointeligente.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FormularioInteligenteRequest {
    private String activityId;
    private String activityName;
    private String policyName;
    private List<FormFieldSchema> formSchema;
    private Map<String, Object> currentValues;
    private String userPrompt;
    private Map<String, Object> context;

    @Data
    public static class FormFieldSchema {
        private String id;
        private String label;
        private String type;
        private boolean required;
        private List<String> options;
    }
}
