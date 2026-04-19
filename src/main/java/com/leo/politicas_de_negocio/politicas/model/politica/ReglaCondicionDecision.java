package com.leo.politicas_de_negocio.politicas.model.politica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReglaCondicionDecision {
    private String campo;
    private String tipo;
    private String operador;
    private Object valor;
}
