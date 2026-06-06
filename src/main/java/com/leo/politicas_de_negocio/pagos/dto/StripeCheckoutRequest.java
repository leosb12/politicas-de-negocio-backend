package com.leo.politicas_de_negocio.pagos.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class StripeCheckoutRequest {
    private String politicaId;
    private String usuarioId;
    private BigDecimal monto;
    private String descripcion;
    private String codigoTramite;
    private Map<String, Object> datosContexto;
    private Map<String, Object> respuestasRequisitosIniciales;
    private String successUrl;
    private String cancelUrl;
}
