package com.leo.politicas_de_negocio.workflow_metricas.repository;

import com.leo.politicas_de_negocio.workflow_metricas.model.MetricaInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MetricaInstanciaRepository extends MongoRepository<MetricaInstancia, String> {
    Optional<MetricaInstancia> findByIdInstancia(String idInstancia);
    List<MetricaInstancia> findByIdPolitica(String idPolitica);
}
