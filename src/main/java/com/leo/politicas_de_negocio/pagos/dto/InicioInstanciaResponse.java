package com.leo.politicas_de_negocio.pagos.dto;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class InicioInstanciaResponse {
    private boolean requierePago;
    private String mensaje;
    private String politicaId;
    private String politicaNombre;
    private BigDecimal montoPago;
    private String monedaPago;
    private String descripcionPago;
    private InstanciaPolitica instancia;
}
