package com.leo.politicas_de_negocio.politicas.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePoliticaRequest {
    private String nombre;
    private String descripcion;
    private String tipoPolitica;
    private String departamentoInicioId;
    private Boolean requierePago;
    private BigDecimal montoPago;
    private String monedaPago;
    private String descripcionPago;
}
