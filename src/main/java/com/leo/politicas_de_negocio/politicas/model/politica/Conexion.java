package com.leo.politicas_de_negocio.politicas.model.politica;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conexion {
    private String origen;
    private String destino;
    private String puertoOrigen;
    private String puertoDestino;
}
