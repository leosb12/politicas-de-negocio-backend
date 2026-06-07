package com.leo.politicas_de_negocio.tareas.repository;

import com.leo.politicas_de_negocio.tareas.model.TareaActividad;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Collection;
import java.util.List;

public interface TareaActividadRepository extends MongoRepository<TareaActividad, String> {

    List<TareaActividad> findByInstanciaIdOrderByFechaCreacionAsc(String instanciaId);

    List<TareaActividad> findByPoliticaIdOrderByFechaCreacionDesc(String politicaId);

    @Query(
            value = "{ 'instanciaId': { '$in': ?0 } }",
            fields = "{ 'instanciaId': 1, 'nodoId': 1, 'estadoTarea': 1, 'fechaCreacion': 1 }"
    )
    List<TareaResumenProjection> findResumenByInstanciaIdIn(Collection<String> instanciaIds);

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

    long countByAsignadoAAndEstadoTareaIn(String asignadoA, List<EstadoTarea> estados);
}
