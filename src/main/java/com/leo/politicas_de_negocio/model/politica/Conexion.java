package com.leo.politicas_de_negocio.model.politica;

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
}
