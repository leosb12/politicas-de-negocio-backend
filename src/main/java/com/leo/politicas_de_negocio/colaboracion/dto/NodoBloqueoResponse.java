package com.leo.politicas_de_negocio.colaboracion.dto;

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
public class NodoBloqueoResponse {

    private String politicaId;
    private String nodeId;
    private List<PresenciaUsuarioResponse> editores;
    private Boolean advertenciaColision;
    private String aviso;
    private LocalDateTime timestamp;
}
