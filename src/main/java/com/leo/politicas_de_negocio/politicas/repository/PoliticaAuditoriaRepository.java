package com.leo.politicas_de_negocio.politicas.repository;

import com.leo.politicas_de_negocio.politicas.model.PoliticaAuditoria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PoliticaAuditoriaRepository extends MongoRepository<PoliticaAuditoria, String> {
    List<PoliticaAuditoria> findByPoliticaIdOrderByFechaDesc(String politicaId);
}
