package com.leo.politicas_de_negocio.politicas.model.politica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrupoCondicionDecision {
    private String operadorLogico;
    private List<ReglaCondicionDecision> reglas;
    private List<GrupoCondicionDecision> grupos;
}
