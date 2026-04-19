package com.leo.politicas_de_negocio.instancias.repository;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InstanciaPoliticaRepository extends MongoRepository<InstanciaPolitica, String> {

    List<InstanciaPolitica> findAllByOrderByFechaCreacionDesc();

    List<InstanciaPolitica> findByCreadaPorOrderByFechaCreacionDesc(String creadaPor);

    List<InstanciaPolitica> findByEstadoInstanciaOrderByFechaCreacionDesc(EstadoInstancia estadoInstancia);
}
