package com.leo.politicas_de_negocio.instancias.repository;

import com.leo.politicas_de_negocio.instancias.model.InstanciaPolitica;
import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface InstanciaPoliticaRepository extends MongoRepository<InstanciaPolitica, String> {

    List<InstanciaPolitica> findAllByOrderByFechaCreacionDesc();

    List<InstanciaPolitica> findByCreadaPorOrderByFechaCreacionDesc(String creadaPor);

    List<InstanciaPolitica> findByPoliticaIdOrderByFechaCreacionDesc(String politicaId);

    List<InstanciaPolitica> findByEstadoInstanciaOrderByFechaCreacionDesc(EstadoInstancia estadoInstancia);

    @Query(
            value = "{ 'creadaPor': ?0 }",
            fields = "{ '_id': 1, 'politicaId': 1, 'codigoTramite': 1, 'estadoInstancia': 1, 'fechaCreacion': 1 }"
    )
    Page<InstanciaCardProjection> findCardsByCreadaPor(String creadaPor, Pageable pageable);
}
