package com.leo.politicas_de_negocio.movilia.clasificacion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisitoInicialDto {
    private String nombre;
    private String label;
    private String tipo;
    private Boolean obligatorio;
}
