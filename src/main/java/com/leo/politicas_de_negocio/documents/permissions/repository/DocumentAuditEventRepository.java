package com.leo.politicas_de_negocio.documents.permissions.repository;

import com.leo.politicas_de_negocio.documents.permissions.model.DocumentAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DocumentAuditEventRepository extends MongoRepository<DocumentAuditEvent, String> {

    List<DocumentAuditEvent> findByDocumentoIdOrderByFechaHoraDesc(String documentoId);

    List<DocumentAuditEvent> findByTramiteIdOrderByFechaHoraDesc(String tramiteId);
}
