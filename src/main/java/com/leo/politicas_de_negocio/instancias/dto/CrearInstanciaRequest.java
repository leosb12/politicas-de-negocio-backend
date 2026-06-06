package com.leo.politicas_de_negocio.instancias.dto;

import lombok.Data;

import java.util.Map;

@Data
public class CrearInstanciaRequest {
    private String politicaId;
    private String codigoTramite;
    private Map<String, Object> datosContexto;
    private Map<String, Object> respuestasRequisitosIniciales;
}
