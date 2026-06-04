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
public class PermisosLecturaSeccion {
    private List<String> departamentos;
    private List<String> roles;
    private List<String> usuarios;
    private Boolean incluirClienteIniciador;
}
