package com.leo.politicas_de_negocio.dto.politica.colaboracion;

import lombok.Data;

@Data
public class NodoEdicionRequest {

    private String actorUserId;
    private String actorNombre;
    private String nodeId;
    private Boolean editing;
}
