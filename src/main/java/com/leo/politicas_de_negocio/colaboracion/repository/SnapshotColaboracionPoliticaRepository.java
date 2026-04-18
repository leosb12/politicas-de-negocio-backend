package com.leo.politicas_de_negocio.colaboracion.repository;

import com.leo.politicas_de_negocio.colaboracion.model.SnapshotColaboracionPolitica;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SnapshotColaboracionPoliticaRepository extends MongoRepository<SnapshotColaboracionPolitica, String> {

    Optional<SnapshotColaboracionPolitica> findTopByPoliticaIdOrderBySecuenciaDesc(String politicaId);

    boolean existsByPoliticaId(String politicaId);
}
