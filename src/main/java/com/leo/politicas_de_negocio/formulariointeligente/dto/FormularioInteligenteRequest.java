package com.leo.politicas_de_negocio.formulariointeligente.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FormularioInteligenteRequest {
    @JsonAlias({"activity_id", "taskId", "task_id"})
    private String activityId;

    @JsonAlias({"activity_name", "taskName", "task_name", "name"})
    private String activityName;

    @JsonAlias({"policy_name", "policyTitle", "policy_title"})
    private String policyName;

    @JsonAlias({"schema", "fields", "form_schema"})
    private List<FormFieldSchema> formSchema;

    @JsonAlias({"values", "formValues", "form_values", "current_values"})
    private Map<String, Object> currentValues;

    @JsonAlias({"prompt", "instruction", "instructions", "user_prompt"})
    private String userPrompt;

    private Map<String, Object> context;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FormFieldSchema {
        @JsonAlias({"fieldId", "field_id", "name"})
        private String id;

        private String label;

        @JsonAlias({"fieldType", "field_type"})
        private String type;

        private boolean required;
        private List<String> options;
    }
}
