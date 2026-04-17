package com.leo.politicas_de_negocio.repository;

import com.leo.politicas_de_negocio.model.PoliticaNegocio;
import com.leo.politicas_de_negocio.model.enums.EstadoPolitica;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoliticaNegocioRepository extends MongoRepository<PoliticaNegocio, String> {
    List<PoliticaNegocio> findByEstado(EstadoPolitica estado);
}
