package com.leo.politicas_de_negocio.tareas.repository;

import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TareaActividadRepository extends MongoRepository<TareaActividad, String> {

    List<TareaActividad> findByInstanciaIdOrderByFechaCreacionAsc(String instanciaId);

    List<TareaActividad> findByInstanciaIdAndNodoIdAndEstadoTareaIn(
            String instanciaId,
            String nodoId,
            List<EstadoTarea> estados
    );

    List<TareaActividad> findByResponsableTipoAndResponsableIdAndEstadoTareaInOrderByFechaCreacionAsc(
            String responsableTipo,
            String responsableId,
            List<EstadoTarea> estados
    );

    List<TareaActividad> findByAsignadoAAndEstadoTareaInOrderByFechaCreacionAsc(
            String asignadoA,
            List<EstadoTarea> estados
    );

    boolean existsByInstanciaIdAndAsignadoA(String instanciaId, String asignadoA);

    boolean existsByInstanciaIdAndResponsableTipoIgnoreCaseAndResponsableId(
            String instanciaId,
            String responsableTipo,
            String responsableId
    );

    long countByInstanciaId(String instanciaId);

    long countByInstanciaIdAndEstadoTarea(String instanciaId, EstadoTarea estadoTarea);

    long countByInstanciaIdAndEstadoTareaIn(String instanciaId, List<EstadoTarea> estados);
}
