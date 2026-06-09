package com.leo.politicas_de_negocio.reportes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelacionReportable {
    private String nombre;
    private String entidadOrigen;
    private String entidadDestino;
    private String campoLocal;
    private String campoDestino;
    private String tipoRelacion; // ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY
    private boolean requiereObjectIdConversion;
    private List<String> camposEnriquecidos;
    private String descripcion;
}
