package com.leo.politicas_de_negocio.repository;

import com.leo.politicas_de_negocio.model.colaboracion.EventoColaboracionAplicado;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface EventoColaboracionAplicadoRepository extends MongoRepository<EventoColaboracionAplicado, String> {

    Optional<EventoColaboracionAplicado> findByPoliticaIdAndEventId(String politicaId, String eventId);

    List<EventoColaboracionAplicado> findTop50ByPoliticaIdOrderBySecuenciaDesc(String politicaId);
}
