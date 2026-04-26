package com.leo.politicas_de_negocio.formulariointeligente.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FormularioInteligenteResponse {
    private boolean success;
    private Map<String, Object> updatedValues;
    private List<FormularioInteligenteChange> changes;
    private List<String> warnings;
    private Double confidence;
    private String message;

    @Data
    public static class FormularioInteligenteChange {
        private String fieldId;
        private Object oldValue;
        private Object newValue;
        private String reason;
    }
}
