package com.leo.politicas_de_negocio.politicas.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TramiteDisponibleResponse {
    private String id;
    private String nombre;
    private String descripcion;
    private String tipoPolitica;
    private String departamentoInicioId;
    private String departamentoInicioNombre;
    private Boolean requierePago;
    private Boolean tieneRequisitosIniciales;
    private BigDecimal montoPago;
    private String monedaPago;
    private String descripcionPago;
}
