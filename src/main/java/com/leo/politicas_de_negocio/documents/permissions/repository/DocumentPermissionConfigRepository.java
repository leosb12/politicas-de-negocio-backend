package com.leo.politicas_de_negocio.documents.permissions.repository;

import com.leo.politicas_de_negocio.documents.permissions.model.DocumentPermissionConfig;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPermissionConfigRepository extends MongoRepository<DocumentPermissionConfig, String> {

    Optional<DocumentPermissionConfig> findByCampoIdAndActivoTrue(String campoId);

    Optional<DocumentPermissionConfig> findFirstByCampoIdOrderByFechaCreacionDesc(String campoId);

    List<DocumentPermissionConfig> findByFormularioIdAndActivoTrueOrderByFechaCreacionDesc(String formularioId);

    boolean existsByCampoIdAndActivoTrue(String campoId);
}
