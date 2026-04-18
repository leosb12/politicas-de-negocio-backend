package com.leo.politicas_de_negocio.colaboracion.dto;

import com.leo.politicas_de_negocio.colaboracion.model.TipoEventoColaboracion;
import com.leo.politicas_de_negocio.politicas.model.politica.Conexion;
import com.leo.politicas_de_negocio.politicas.model.politica.Nodo;
import lombok.Data;

import java.util.List;

@Data
public class ColaboracionEventoRequest {

    private String eventId;
    private String actorUserId;
    private TipoEventoColaboracion tipo;

    private Long expectedSequence;
    private String nodeId;
    private Long expectedNodeVersion;

    private Double posX;
    private Double posY;

    private Nodo nodo;
    private Conexion conexion;

    // Solo para REPLACE_FLOW.
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
}
