package com.leo.politicas_de_negocio.colaboracion.dto;

import com.leo.politicas_de_negocio.politicas.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColaboracionEstadoResponse {

    private String politicaId;
    private EstadoPolitica estadoPolitica;
    private Long secuenciaActual;
    private String laneOrientation;
    private Double laneWidth;
    private Double laneHeight;
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    private LocalDateTime fechaUltimaColaboracion;
    private LocalDateTime fechaActualizacion;
}
