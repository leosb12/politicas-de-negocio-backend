package com.leo.politicas_de_negocio.reportes.dto;

import lombok.Data;

@Data
public class FiltroDto {
    private String campo;
    private String operador;
    private Object valor;
}
