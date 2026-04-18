package com.leo.politicas_de_negocio.colaboracion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresenciaUsuarioResponse {

    private String userId;
    private String nombre;
    private Integer sesionesActivas;
    private LocalDateTime ultimaActividad;
}
