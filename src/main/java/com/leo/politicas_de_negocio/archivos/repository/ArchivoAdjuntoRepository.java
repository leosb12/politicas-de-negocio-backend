package com.leo.politicas_de_negocio.archivos.repository;

import com.leo.politicas_de_negocio.archivos.model.ArchivoAdjunto;
import com.leo.politicas_de_negocio.archivos.model.enums.EstadoArchivo;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ArchivoAdjuntoRepository extends MongoRepository<ArchivoAdjunto, String> {

    Optional<ArchivoAdjunto> findByIdAndEstado(String id, EstadoArchivo estado);

    List<ArchivoAdjunto> findByInstanciaIdAndEstadoOrderByFechaSubidaDesc(String instanciaId, EstadoArchivo estado);

    List<ArchivoAdjunto> findByActividadIdAndEstadoOrderByFechaSubidaDesc(String actividadId, EstadoArchivo estado);

    List<ArchivoAdjunto> findByTareaIdAndEstadoOrderByFechaSubidaDesc(String tareaId, EstadoArchivo estado);

    List<ArchivoAdjunto> findByPoliticaIdAndEstadoOrderByFechaSubidaDesc(String politicaId, EstadoArchivo estado);

    List<ArchivoAdjunto> findByUsuarioIdAndEstadoOrderByFechaSubidaDesc(String usuarioId, EstadoArchivo estado);
}
