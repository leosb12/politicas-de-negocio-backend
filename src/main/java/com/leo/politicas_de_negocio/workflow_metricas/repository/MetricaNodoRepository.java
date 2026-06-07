package com.leo.politicas_de_negocio.workflow_metricas.repository;

import com.leo.politicas_de_negocio.workflow_metricas.model.MetricaNodo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetricaNodoRepository extends MongoRepository<MetricaNodo, String> {
    List<MetricaNodo> findByIdInstancia(String idInstancia);
    List<MetricaNodo> findByIdPolitica(String idPolitica);
    List<MetricaNodo> findByIdInstanciaAndIdNodo(String idInstancia, String idNodo);
    Optional<MetricaNodo> findFirstByIdInstanciaAndIdNodoOrderByFechaEntradaDesc(String idInstancia, String idNodo);
}
