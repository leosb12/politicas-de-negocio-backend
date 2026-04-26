package com.leo.politicas_de_negocio.instancias.repository;

import com.leo.politicas_de_negocio.instancias.model.enums.EstadoInstancia;

import java.time.LocalDateTime;

public interface InstanciaCardProjection {
    String getId();

    String getPoliticaId();

    String getCodigoTramite();

    EstadoInstancia getEstadoInstancia();

    LocalDateTime getFechaCreacion();
}
