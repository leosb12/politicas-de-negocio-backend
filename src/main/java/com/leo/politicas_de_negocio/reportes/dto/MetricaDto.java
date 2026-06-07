package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;

@Data
public class MetricaDto {
    private String operacion;
    private String campo;
    private String alias;
}
