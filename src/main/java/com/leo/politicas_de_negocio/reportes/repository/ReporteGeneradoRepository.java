package com.leo.politicas_de_negocio.reportes.repository;

import com.leo.politicas_de_negocio.reportes.model.ReporteGenerado;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReporteGeneradoRepository extends MongoRepository<ReporteGenerado, String> {
    List<ReporteGenerado> findTop20ByOrderByFechaGeneracionDesc();
}
