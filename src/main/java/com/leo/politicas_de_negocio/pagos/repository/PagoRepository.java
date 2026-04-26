package com.leo.politicas_de_negocio.pagos.repository;

import com.leo.politicas_de_negocio.pagos.model.Pago;
import com.leo.politicas_de_negocio.pagos.model.enums.EstadoPago;
import com.leo.politicas_de_negocio.pagos.model.enums.ProveedorPago;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends MongoRepository<Pago, String> {

    Optional<Pago> findByStripeSessionId(String stripeSessionId);

    List<Pago> findByUsuarioIdAndPoliticaIdAndEstadoInOrderByFechaCreacionDesc(
            String usuarioId,
            String politicaId,
            Collection<EstadoPago> estados
    );

    Optional<Pago> findFirstByUsuarioIdAndPoliticaIdAndProveedorAndEstadoInOrderByFechaCreacionDesc(
            String usuarioId,
            String politicaId,
            ProveedorPago proveedor,
            Collection<EstadoPago> estados
    );
}
