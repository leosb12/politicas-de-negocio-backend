package com.leo.politicas_de_negocio.politicas.repository;

import com.leo.politicas_de_negocio.politicas.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PoliticaNegocioRepository extends MongoRepository<PoliticaNegocio, String> {
    List<PoliticaNegocio> findByEstado(EstadoPolitica estado);

    @Query(value = "{ '_id': { '$in': ?0 } }", fields = "{ '_id': 1, 'nombre': 1 }")
    List<PoliticaNombreProjection> findNombreByIdIn(Collection<String> ids);

    @Aggregation(pipeline = {
            "{ '$match': { '_id': { '$in': ?0 } } }",
            "{ '$project': { 'id': '$_id', 'nombre': 1, 'totalNodos': { '$size': { '$ifNull': [ '$nodos', [] ] } } } }"
    })
    List<PoliticaCardInfoProjection> findCardInfoByIdIn(Collection<String> ids);
}
