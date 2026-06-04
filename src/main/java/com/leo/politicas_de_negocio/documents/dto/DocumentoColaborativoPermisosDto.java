package com.leo.politicas_de_negocio.documents.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentoColaborativoPermisosDto {
    private boolean puedeLeer;
    private boolean puedeEditar;
    private boolean puedeDescargar;
    private boolean puedeImprimir;
    private boolean puedeComentar;
    private boolean puedeReemplazar;
    private boolean puedeEliminar;
    private boolean puedeCompartirInternamente;
}
