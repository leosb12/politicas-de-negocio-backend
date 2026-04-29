package com.leo.politicas_de_negocio.tareas.repository;

import com.leo.politicas_de_negocio.tareas.model.enums.EstadoTarea;

import java.time.LocalDateTime;

public interface TareaResumenProjection {
    String getInstanciaId();

    String getNodoId();

    EstadoTarea getEstadoTarea();

    LocalDateTime getFechaCreacion();
}
