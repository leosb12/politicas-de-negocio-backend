package com.leo.politicas_de_negocio.pagos.model;

import com.leo.politicas_de_negocio.pagos.model.enums.EstadoPago;
import com.leo.politicas_de_negocio.pagos.model.enums.ProveedorPago;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "pagos")
@CompoundIndex(name = "idx_pago_usuario_politica_estado", def = "{'usuarioId': 1, 'politicaId': 1, 'estado': 1, 'fechaCreacion': -1}")
@CompoundIndex(name = "idx_pago_stripe_session", def = "{'stripeSessionId': 1}", unique = true, sparse = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    private String id;

    @Indexed(name = "idx_pago_politica")
    private String politicaId;
    private String instanciaId;
    @Indexed(name = "idx_pago_usuario")
    private String usuarioId;
    private ProveedorPago proveedor;
    private BigDecimal monto;
    private String moneda;
    private String descripcion;
    private EstadoPago estado;
    private String stripeSessionId;
    private String paypalUrl;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaConfirmacion;

    private String codigoTramite;
    private Map<String, Object> datosContexto;
}
