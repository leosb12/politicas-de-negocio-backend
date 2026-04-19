package com.leo.politicas_de_negocio.tareas.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CompletarTareaRequest {
    private Map<String, Object> formularioRespuesta;
    private String observaciones;
}
