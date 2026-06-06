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
public class PoliticaClasificacionDto {
    private String id;
    private String nombre;
    private String descripcion;
    private String categoria;
    private String descripcionClasificacion;
    private List<String> palabrasClave;
    private List<String> intencionesEjemplo;
    private List<String> requisitosSugeridos;
    private List<RequisitoInicialDto> requisitosIniciales;
}
