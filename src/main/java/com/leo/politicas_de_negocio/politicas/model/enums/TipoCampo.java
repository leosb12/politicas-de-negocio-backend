package com.leo.politicas_de_negocio.politicas.model.enums;

public enum TipoCampo {
    TEXTO,
    NUMERO,
    BOOLEANO,
    ARCHIVO,
    FECHA,
    CHECKBOX,
    SELECCION,
    GRID,
    LABEL,
    DOCUMENTO_COLABORATIVO;

    public static TipoCampo fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");
        for (TipoCampo tc : TipoCampo.values()) {
            if (tc.name().equals(normalized)) {
                return tc;
            }
        }
        if (esAliasDocumentoColaborativo(normalized)) {
            return DOCUMENTO_COLABORATIVO;
        }
        return null;
    }

    private static boolean esAliasDocumentoColaborativo(String normalized) {
        return switch (normalized) {
            case "WORD", "DOC", "DOCX", "DOCUMENTO_WORD",
                 "EXCEL", "XLS", "XLSX", "DOCUMENTO_EXCEL",
                 "POWERPOINT", "PPT", "PPTX", "DOCUMENTO_POWERPOINT" -> true;
            default -> false;
        };
    }
}
