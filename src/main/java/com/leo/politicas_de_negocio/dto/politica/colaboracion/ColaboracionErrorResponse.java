package com.leo.politicas_de_negocio.dto.politica.colaboracion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColaboracionErrorResponse {

    private String politicaId;
    private String eventId;
    private String codigo;
    private String mensaje;
    private Long secuenciaActual;
    private LocalDateTime timestamp;
}
