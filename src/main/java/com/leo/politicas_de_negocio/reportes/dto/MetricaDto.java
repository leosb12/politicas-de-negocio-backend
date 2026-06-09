package com.leo.politicas_de_negocio.reportes.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricaDto {
    private String operacion;
    private String campo;
    private String alias;
}
