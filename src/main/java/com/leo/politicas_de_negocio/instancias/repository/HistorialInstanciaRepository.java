package com.leo.politicas_de_negocio.instancias.repository;

import com.leo.politicas_de_negocio.instancias.model.HistorialInstancia;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HistorialInstanciaRepository extends MongoRepository<HistorialInstancia, String> {

    List<HistorialInstancia> findByInstanciaIdOrderByFechaAsc(String instanciaId);
}
