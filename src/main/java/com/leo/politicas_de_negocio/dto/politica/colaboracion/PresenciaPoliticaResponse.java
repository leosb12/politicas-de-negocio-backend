package com.leo.politicas_de_negocio.dto.politica.colaboracion;

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
public class PresenciaPoliticaResponse {

    private String politicaId;
    private Integer totalUsuariosConectados;
    private List<PresenciaUsuarioResponse> usuarios;
    private LocalDateTime timestamp;
}
