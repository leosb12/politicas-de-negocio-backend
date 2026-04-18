package com.leo.politicas_de_negocio.repository;

import com.leo.politicas_de_negocio.model.colaboracion.SnapshotColaboracionPolitica;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SnapshotColaboracionPoliticaRepository extends MongoRepository<SnapshotColaboracionPolitica, String> {

    Optional<SnapshotColaboracionPolitica> findTopByPoliticaIdOrderBySecuenciaDesc(String politicaId);
}
