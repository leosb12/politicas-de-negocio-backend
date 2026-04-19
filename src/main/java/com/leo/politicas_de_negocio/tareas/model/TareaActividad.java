package com.leo.politicas_de_negocio.tareas.model;

import com.leo.politicas_de_negocio.politicas.model.politica.CampoFormulario;
import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "tareas_actividad")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TareaActividad {

    @Id
    private String id;

    private String instanciaId;
    private String politicaId;

    private String nodoId;
    private String nombreNodo;

    private String responsableTipo;
    private String responsableId;

    private EstadoTarea estadoTarea;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;

    private String asignadoA;

    private List<CampoFormulario> formularioDefinicion;
    private Map<String, Object> formularioRespuesta;
    private String observaciones;
}
