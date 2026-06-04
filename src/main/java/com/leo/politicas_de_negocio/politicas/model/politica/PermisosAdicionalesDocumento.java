package com.leo.politicas_de_negocio.politicas.model.politica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermisosAdicionalesDocumento {
    private Boolean puedeDescargar;
    private Boolean puedeImprimir;
    private Boolean puedeComentar;
    private Boolean puedeReemplazar;
    private Boolean puedeEliminar;
    private Boolean puedeCompartirInternamente;
}
