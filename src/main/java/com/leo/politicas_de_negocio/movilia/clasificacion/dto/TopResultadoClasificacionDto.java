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
public class TopResultadoClasificacionDto {
    private String politicaId;
    private String nombrePolitica;
    private Double confianza;
    private Double scoreRequisitos;
    private Double scoreSemantico;
    private Double scoreFinal;
    private List<String> requisitosCoincidentes;
    private List<String> requisitosFaltantes;
}
