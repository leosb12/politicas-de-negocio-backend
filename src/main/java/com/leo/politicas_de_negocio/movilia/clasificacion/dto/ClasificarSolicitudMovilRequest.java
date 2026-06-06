package com.leo.politicas_de_negocio.movilia.clasificacion.dto;

import lombok.Data;

@Data
public class ClasificarSolicitudMovilRequest {
    private String texto;
    private Boolean usarDeepSeek;
}
