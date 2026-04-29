package com.leo.politicas_de_negocio.politicas.model.politica;

import com.leo.politicas_de_negocio.politicas.model.enums.TipoCampo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampoFormulario {
    private String campo;
    private TipoCampo tipo;
    private String etiqueta;
    private Boolean requerido;
    private String placeholder;
    private String ayuda;
    private Integer orden;
    private List<String> opciones;
    private Map<String, Object> validaciones;
}
