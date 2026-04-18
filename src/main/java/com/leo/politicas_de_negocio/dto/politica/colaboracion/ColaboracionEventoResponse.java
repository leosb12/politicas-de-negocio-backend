package com.leo.politicas_de_negocio.dto.politica.colaboracion;

import com.leo.politicas_de_negocio.model.colaboracion.TipoEventoColaboracion;
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
public class ColaboracionEventoResponse {

    private String politicaId;
    private String eventId;
    private String actorUserId;
    private TipoEventoColaboracion tipo;

    private Long secuencia;
    private String estado;
    private String detalle;

    private String nodeId;
    private Long nodeVersion;

    private Double posX;
    private Double posY;

    private Nodo nodo;
    private Conexion conexion;
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
    private LocalDateTime serverTimestamp;
}
