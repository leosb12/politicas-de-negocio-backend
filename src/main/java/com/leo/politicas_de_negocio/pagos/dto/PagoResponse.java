package com.leo.politicas_de_negocio.pagos.dto;

import com.leo.politicas_de_negocio.pagos.model.enums.EstadoPago;
import com.leo.politicas_de_negocio.pagos.model.enums.ProveedorPago;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PagoResponse {
    private String id;
    private String politicaId;
    private String instanciaId;
    private String usuarioId;
    private ProveedorPago proveedor;
    private BigDecimal monto;
    private String moneda;
    private String descripcion;
    private EstadoPago estado;
    private String stripeSessionId;
    private String stripeCheckoutUrl;
    private String paypalUrl;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaConfirmacion;
}
