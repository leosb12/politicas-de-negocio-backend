package com.leo.politicas_de_negocio.dto.politica;

import com.leo.politicas_de_negocio.model.politica.Conexion;
import com.leo.politicas_de_negocio.model.politica.Nodo;
import lombok.Data;

import java.util.List;

@Data
public class UpdateFlujoRequest {
    private List<Nodo> nodos;
    private List<Conexion> conexiones;
}
