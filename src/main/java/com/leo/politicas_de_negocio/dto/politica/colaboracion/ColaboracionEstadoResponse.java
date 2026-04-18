package com.leo.politicas_de_negocio.dto.politica.colaboracion;

import com.leo.politicas_de_negocio.model.enums.EstadoPolitica;
import com.leo.politicas_de_negocio.model.politica.Conexion;
import com.leo.politicas_de_negocio.model.politica.Nodo;
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
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    private LocalDateTime fechaUltimaColaboracion;
    private LocalDateTime fechaActualizacion;
}
