package com.leo.politicas_de_negocio.analiticas.repository;

import com.leo.politicas_de_negocio.analiticas.model.AuditoriaSistema;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditoriaSistemaRepository extends MongoRepository<AuditoriaSistema, String> {
    List<AuditoriaSistema> findAllByOrderByFechaDesc();
}
