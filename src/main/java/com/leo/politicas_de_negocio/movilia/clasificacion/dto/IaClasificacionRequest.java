package com.leo.politicas_de_negocio.movilia.clasificacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IaClasificacionRequest {
    private String texto;
    private String canal;
    private List<PoliticaClasificacionDto> politicas;
    private Boolean usarDeepSeek;
    private String nombreDocumento;
}
