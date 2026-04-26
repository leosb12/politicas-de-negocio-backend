package com.leo.politicas_de_negocio.iaflujo.dto;

import lombok.Data;

import java.util.List;

@Data
public class TextoAFlujoRequest {
    private String descripcion;
    private ContextoGeneracion context;

    @Data
    public static class ContextoGeneracion {
        private List<DepartamentoContexto> departamentos;
    }

    @Data
    public static class DepartamentoContexto {
        private String id;
        private String nombre;
    }
}
